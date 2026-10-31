package com.riyadm.smalihelperplus.core.smali.parser

import com.riyadm.smalihelperplus.core.smali.ast.AnnotationBlock
import com.riyadm.smalihelperplus.core.smali.ast.CatchDirective
import com.riyadm.smalihelperplus.core.smali.ast.ClassHeader
import com.riyadm.smalihelperplus.core.smali.ast.CountDirective
import com.riyadm.smalihelperplus.core.smali.ast.DebugDirective
import com.riyadm.smalihelperplus.core.smali.ast.FieldDefinition
import com.riyadm.smalihelperplus.core.smali.ast.Instruction
import com.riyadm.smalihelperplus.core.smali.ast.LabelDefinition
import com.riyadm.smalihelperplus.core.smali.ast.LabelOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralType
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.MethodItem
import com.riyadm.smalihelperplus.core.smali.ast.Operand
import com.riyadm.smalihelperplus.core.smali.ast.ParamDirective
import com.riyadm.smalihelperplus.core.smali.ast.PayloadDirective
import com.riyadm.smalihelperplus.core.smali.ast.ReferenceOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterListOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterRangeOperand
import com.riyadm.smalihelperplus.core.smali.ast.SmaliFile
import com.riyadm.smalihelperplus.core.smali.ast.SourceRange
import com.riyadm.smalihelperplus.core.smali.ast.StringOperand
import com.riyadm.smalihelperplus.core.smali.ast.TypeDirective
import com.riyadm.smalihelperplus.core.smali.diagnostics.Diagnostic
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity
import com.riyadm.smalihelperplus.core.smali.lexer.LexedLine
import com.riyadm.smalihelperplus.core.smali.lexer.SmaliLexer
import com.riyadm.smalihelperplus.core.smali.lexer.Token
import com.riyadm.smalihelperplus.core.smali.lexer.TokenType
import com.riyadm.smalihelperplus.core.smali.model.Descriptors
import com.riyadm.smalihelperplus.core.smali.opcodes.DalvikOpcodeTable
import com.riyadm.smalihelperplus.core.smali.opcodes.PayloadKind
import java.math.BigInteger

enum class ParseMode {
    /** A complete `.smali` file: instructions outside methods are errors. */
    FILE,

    /** Accepts code snippets: when the input has no `.method`, all instructions form one implicit method. */
    LENIENT,
}

data class ParseResult(val file: SmaliFile, val diagnostics: List<Diagnostic>)

/**
 * Hand-written recursive-descent style parser for smali source. It recovers from errors line by line and records
 * every problem as a [Diagnostic]; it never throws on malformed input.
 */
object SmaliParser {
    val ACCESS_FLAGS = setOf(
        "public", "private", "protected", "static", "final", "synchronized", "volatile", "bridge", "transient",
        "varargs", "native", "interface", "abstract", "strictfp", "synthetic", "annotation", "enum", "constructor",
        "declared-synchronized",
    )

    val DIRECTIVES = listOf(
        ".class", ".super", ".source", ".implements", ".field", ".end field", ".method", ".end method", ".registers",
        ".locals", ".param", ".end param", ".annotation", ".end annotation", ".subannotation", ".end subannotation",
        ".enum", ".line", ".local", ".end local", ".restart local", ".prologue", ".epilogue", ".catch", ".catchall",
        ".packed-switch", ".end packed-switch", ".sparse-switch", ".end sparse-switch", ".array-data",
        ".end array-data",
    )

    fun parse(text: String, mode: ParseMode = ParseMode.FILE): ParseResult = Parser(SmaliLexer.lex(text), mode).run()

    private class MethodBuilder(
        val flags: List<String>,
        val name: String,
        val protoText: String,
        val startLine: Int,
        val nameRange: SourceRange,
        val implicit: Boolean,
    ) {
        val items = ArrayList<MethodItem>()
        var registers: CountDirective? = null
        var locals: CountDirective? = null
        var instructionCount = 0
        var openParamIndex = -1
        val labelsSinceItem = ArrayList<String>()

        fun build(endLine: Int?) = MethodDefinition(
            flags = flags, name = name, protoText = protoText, proto = Descriptors.parseProto(protoText),
            startLine = startLine, endLine = endLine, registers = registers, locals = locals, items = items.toList(),
            nameRange = nameRange, isImplicit = implicit,
        )
    }

    private class FieldBuilder(
        val flags: List<String>,
        val name: String,
        val type: String,
        val value: String?,
        val line: Int,
        val nameRange: SourceRange,
    ) {
        val annotations = ArrayList<AnnotationBlock>()
        fun build(endLine: Int?) = FieldDefinition(flags, name, type, value, line, endLine, annotations.toList(), nameRange)
    }

    private class PayloadBuilder(val kind: PayloadKind, val line: Int, val labels: List<String>, val header: Long?) {
        val keys = ArrayList<Long>()
        val targets = ArrayList<LabelOperand>()
        val values = ArrayList<String>()
        fun build(endLine: Int?) = PayloadDirective(
            kind = kind, line = line, endLine = endLine, labels = labels,
            firstKey = if (kind == PayloadKind.PACKED_SWITCH) header else null,
            keys = keys.toList(), targets = targets.toList(),
            elementWidth = if (kind == PayloadKind.ARRAY_DATA) header?.toInt() else null, values = values.toList(),
        )
    }

    /** Where a skipped annotation block should be attached once closed. */
    private enum class AnnotationOwner { CLASS, FIELD, METHOD }

    private class AnnotationState(val owner: AnnotationOwner, val visibility: String?, val type: String?, val line: Int) {
        var depth = 0
    }

    private class Parser(private val lines: List<LexedLine>, private val mode: ParseMode) {
        private val diagnostics = ArrayList<Diagnostic>()
        private var header: ClassHeader? = null
        private var superClass: TypeDirective? = null
        private var source: String? = null
        private val interfaces = ArrayList<TypeDirective>()
        private val classAnnotations = ArrayList<AnnotationBlock>()
        private val fields = ArrayList<FieldDefinition>()
        private val methods = ArrayList<MethodDefinition>()
        private val stray = ArrayList<Instruction>()

        private var method: MethodBuilder? = null
        private var field: FieldBuilder? = null
        private var payload: PayloadBuilder? = null
        private var annotation: AnnotationState? = null
        private var strayIndex = 0
        private val fragment = mode == ParseMode.LENIENT && lines.none { l ->
            l.codeTokens.firstOrNull()?.let { it.type == TokenType.DIRECTIVE && it.text == ".method" } == true
        } && lines.any { l -> l.codeTokens.firstOrNull()?.type == TokenType.WORD }

        fun run(): ParseResult {
            if (fragment) {
                method = MethodBuilder(emptyList(), "snippet", "", 1, SourceRange(1, 0, 0), implicit = true)
            }
            for (line in lines) processLine(line)
            finish()
            val file = SmaliFile(
                header = header, superClass = superClass, source = source, interfaces = interfaces.toList(),
                annotations = classAnnotations.toList(), fields = fields.toList(), methods = methods.toList(),
                strayInstructions = stray.toList(), lines = lines, isFragment = fragment,
            )
            return ParseResult(file, diagnostics.sortedWith(compareBy({ it.line }, { it.startColumn })))
        }

        private fun error(code: DiagnosticCode, message: String, line: Int, start: Int = 0, end: Int = 0, suggestion: String? = null) {
            diagnostics += Diagnostic(Severity.ERROR, code, message, line, start, end, suggestion)
        }

        private fun warning(code: DiagnosticCode, message: String, line: Int, start: Int = 0, end: Int = 0, suggestion: String? = null) {
            diagnostics += Diagnostic(Severity.WARNING, code, message, line, start, end, suggestion)
        }

        private fun finish() {
            val last = lines.lastOrNull()?.number ?: 1
            annotation?.let { a ->
                error(DiagnosticCode.MISSING_END, "Annotation is missing `.end annotation`", a.line, suggestion = "Add `.end annotation` after the annotation body")
                closeAnnotation(null)
            }
            payload?.let { p ->
                error(DiagnosticCode.MISSING_END, "${p.kind.directive} is missing `.end ${p.kind.directive.removePrefix(".")}`", p.line)
                closePayload(null)
            }
            closeField(null)
            method?.let { m ->
                if (m.implicit) {
                    methods += m.build(last)
                } else {
                    error(
                        DiagnosticCode.MISSING_END, "Method `${m.name}` is missing `.end method`", m.startLine,
                        m.nameRange.startColumn, m.nameRange.endColumn, "Add `.end method` after the last instruction",
                    )
                    methods += m.build(null)
                }
                method = null
            }
        }

        private fun processLine(line: LexedLine) {
            line.tokens.firstOrNull { it.type == TokenType.ERROR }?.let {
                error(DiagnosticCode.UNTERMINATED_LITERAL, "Unterminated ${if (it.text.startsWith("'")) "character" else "string"} literal", line.number, it.column, it.endColumn, "Close the literal with a matching quote")
            }
            line.tokens.filter { it.type == TokenType.STRING || it.type == TokenType.CHAR }.forEach { token ->
                val body = token.text.substring(1, token.text.length - 1)
                var i = 0
                var valid = true
                while (i < body.length) {
                    if (body[i] == '\\') {
                        i++
                        if (i >= body.length) { valid = false; break }
                        if (body[i] == 'u') {
                            if (i + 4 >= body.length || body.substring(i + 1, i + 5).any { it.digitToIntOrNull(16) == null }) {
                                valid = false; break
                            }
                            i += 4
                        } else if (body[i] !in "btnfr\"'\\") { valid = false; break }
                    }
                    i++
                }
                if (!valid || (token.type == TokenType.CHAR && parseCharLiteral(token.text) == null)) {
                    error(DiagnosticCode.SYNTAX, "Invalid literal or escape sequence", line.number, token.column, token.endColumn)
                }
            }
            var tokens = line.codeTokens
            if (tokens.isEmpty()) return

            annotation?.let { a ->
                if (handleAnnotationLine(a, tokens, line)) return
            }
            payload?.let { p ->
                if (handlePayloadLine(p, tokens, line)) return
            }

            // Labels at the start of a line (possibly followed by more code).
            while (tokens.isNotEmpty() && tokens[0].type == TokenType.LABEL) {
                val t = tokens[0]
                val m = method
                if (m == null) {
                    error(DiagnosticCode.OUTSIDE_METHOD, "Label `${t.text}` is outside of a method", line.number, t.column, t.endColumn)
                } else {
                    closeParam(m)
                    m.items += LabelDefinition(t.text.removePrefix(":"), line.number, range(line, t, t))
                    m.labelsSinceItem += t.text.removePrefix(":")
                }
                tokens = tokens.drop(1)
            }
            if (tokens.isEmpty()) return

            val first = tokens[0]
            if (first.type == TokenType.DIRECTIVE) {
                handleDirective(first, tokens, line)
            } else {
                handleInstruction(tokens, line)
            }
        }

        // ----------------------------------------------------------------------------------------------------------
        // Annotations
        // ----------------------------------------------------------------------------------------------------------

        /** Returns true when the line was consumed by the open annotation block. */
        private fun handleAnnotationLine(a: AnnotationState, tokens: List<Token>, line: LexedLine): Boolean {
            val first = tokens[0]
            if (tokens.drop(1).any { it.type == TokenType.DIRECTIVE && it.text == ".subannotation" }) a.depth++
            if (first.type == TokenType.DIRECTIVE) {
                when (first.text) {
                    ".subannotation" -> { a.depth++; return true }
                    ".end" -> {
                        when (tokens.getOrNull(1)?.text) {
                            "subannotation" -> { if (a.depth > 0) a.depth--; return true }
                            "annotation" -> {
                                if (a.depth == 0) { closeAnnotation(line.number); return true }
                                // `.end annotation` while a subannotation is open: treat as closing everything.
                                error(DiagnosticCode.MISSING_END, "Sub-annotation is missing `.end subannotation`", line.number, first.column, tokens[1].endColumn)
                                closeAnnotation(line.number)
                                return true
                            }
                            "method", "field", "param" -> {
                                error(DiagnosticCode.MISSING_END, "Annotation is missing `.end annotation`", a.line, suggestion = "Add `.end annotation` before line ${line.number}")
                                closeAnnotation(null)
                                return false
                            }
                        }
                    }
                    // A nested `.annotation` (or a member directive) is never valid inside an annotation body.
                    ".method", ".field", ".class", ".super", ".annotation" -> {
                        error(DiagnosticCode.MISSING_END, "Annotation is missing `.end annotation`", a.line, suggestion = "Add `.end annotation` before line ${line.number}")
                        closeAnnotation(null)
                        return false
                    }
                }
            }
            return true // annotation body content is skipped (structure only)
        }

        private fun openAnnotation(owner: AnnotationOwner, tokens: List<Token>, line: LexedLine) {
            val visibility = tokens.getOrNull(1)?.text?.takeIf { it in setOf("build", "runtime", "system") }
            val typeToken = tokens.getOrNull(if (visibility != null) 2 else 1)
            if (visibility == null && tokens.size > 1) {
                error(DiagnosticCode.SYNTAX, "Annotation visibility must be build, runtime or system", line.number, tokens[1].column, tokens[1].endColumn)
            }
            val type = typeToken?.text
            if (type == null) {
                error(DiagnosticCode.SYNTAX, "Expected an annotation type", line.number, tokens[0].column, tokens[0].endColumn)
            } else if (!Descriptors.isValidClassDescriptor(type)) {
                error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid annotation type `$type`", line.number, typeToken.column, typeToken.endColumn)
            }
            annotation = AnnotationState(owner, visibility, type, line.number)
        }

        private fun closeAnnotation(endLine: Int?) {
            val a = annotation ?: return
            annotation = null
            val block = AnnotationBlock(a.visibility, a.type, a.line, endLine)
            when (a.owner) {
                AnnotationOwner.CLASS -> classAnnotations += block
                AnnotationOwner.FIELD -> field?.annotations?.add(block) ?: classAnnotations.add(block)
                AnnotationOwner.METHOD -> method?.items?.add(block)
            }
        }

        // ----------------------------------------------------------------------------------------------------------
        // Payloads
        // ----------------------------------------------------------------------------------------------------------

        private fun handlePayloadLine(p: PayloadBuilder, tokens: List<Token>, line: LexedLine): Boolean {
            val first = tokens[0]
            if (first.type == TokenType.DIRECTIVE) {
                if (first.text == ".end") {
                    val what = tokens.getOrNull(1)?.text
                    val expected = p.kind.directive.removePrefix(".")
                    if (what == expected) { closePayload(line.number); return true }
                    error(DiagnosticCode.MISSING_END, "${p.kind.directive} is missing `.end $expected`", p.line, suggestion = "Add `.end $expected` before line ${line.number}")
                    closePayload(null)
                    return false
                }
                error(DiagnosticCode.MISSING_END, "${p.kind.directive} is missing `.end ${p.kind.directive.removePrefix(".")}`", p.line)
                closePayload(null)
                return false
            }
            when (p.kind) {
                PayloadKind.PACKED_SWITCH -> tokens.forEach { t ->
                    if (t.type == TokenType.LABEL) p.targets += LabelOperand(t.text, range(line, t, t))
                    else error(DiagnosticCode.SYNTAX, "Packed-switch payload entries must be labels", line.number, t.column, t.endColumn)
                }
                PayloadKind.SPARSE_SWITCH -> {
                    val key = tokens.getOrNull(0)
                    val arrow = tokens.getOrNull(1)
                    val target = tokens.getOrNull(2)
                    val keyValue = key?.let { parseLiteral(it.text, range(line, it, it)) }?.value
                    if (key == null || keyValue == null || arrow?.text != "->" || target?.type != TokenType.LABEL || tokens.size != 3) {
                        error(DiagnosticCode.SYNTAX, "Sparse-switch entries must look like `0x1 -> :sswitch_0`", line.number, tokens.first().column, tokens.last().endColumn)
                    } else {
                        p.keys += keyValue.toLong()
                        p.targets += LabelOperand(target.text, range(line, target, target))
                    }
                }
                PayloadKind.ARRAY_DATA -> tokens.forEach { t ->
                    if (parseLiteral(t.text, range(line, t, t)) != null) p.values += t.text
                    else error(DiagnosticCode.SYNTAX, "Array-data payload entries must be numeric literals", line.number, t.column, t.endColumn)
                }
            }
            return true
        }

        private fun closePayload(endLine: Int?) {
            val p = payload ?: return
            payload = null
            method?.items?.add(p.build(endLine))
        }

        // ----------------------------------------------------------------------------------------------------------
        // Directives
        // ----------------------------------------------------------------------------------------------------------

        private fun handleDirective(first: Token, tokens: List<Token>, line: LexedLine) {
            val m = method
            if (m != null) {
                handleMethodDirective(m, first, tokens, line)
            } else {
                handleClassDirective(first, tokens, line)
            }
        }

        private fun handleClassDirective(first: Token, tokens: List<Token>, line: LexedLine) {
            val n = line.number
            if (first.text != ".annotation" && first.text != ".end") closeField(null)
            when (first.text) {
                ".class" -> {
                    val words = tokens.drop(1)
                    val type = words.lastOrNull()
                    if (type == null) {
                        error(DiagnosticCode.SYNTAX, "Expected a class descriptor after .class", n, first.column, first.endColumn)
                        return
                    }
                    if (header != null) error(DiagnosticCode.DUPLICATE_DIRECTIVE, "Duplicate .class directive", n, first.column, first.endColumn)
                    val flags = words.dropLast(1).map { it.text }
                    checkFlags(words.dropLast(1), line)
                    if (!Descriptors.isValidClassDescriptor(type.text)) {
                        error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid class descriptor `${type.text}`", n, type.column, type.endColumn, "Class descriptors look like Lcom/acme/demo/MyClass;")
                    }
                    if (header == null) header = ClassHeader(flags, type.text, n, range(line, type, type))
                }
                ".super" -> {
                    val type = tokens.getOrNull(1)
                    if (type == null) { error(DiagnosticCode.SYNTAX, "Expected a class descriptor after .super", n, first.column, first.endColumn); return }
                    if (superClass != null) error(DiagnosticCode.DUPLICATE_DIRECTIVE, "Duplicate .super directive", n, first.column, first.endColumn)
                    if (!Descriptors.isValidClassDescriptor(type.text)) error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid superclass descriptor `${type.text}`", n, type.column, type.endColumn)
                    if (superClass == null) superClass = TypeDirective(type.text, n, range(line, type, type))
                }
                ".implements" -> {
                    val type = tokens.getOrNull(1)
                    if (type == null) { error(DiagnosticCode.SYNTAX, "Expected an interface descriptor after .implements", n, first.column, first.endColumn); return }
                    if (!Descriptors.isValidClassDescriptor(type.text)) error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid interface descriptor `${type.text}`", n, type.column, type.endColumn)
                    interfaces += TypeDirective(type.text, n, range(line, type, type))
                }
                ".source" -> {
                    val s = tokens.getOrNull(1)
                    if (s == null || s.type != TokenType.STRING) error(DiagnosticCode.SYNTAX, "Expected a string after .source", n, first.column, first.endColumn)
                    else source = s.text.removeSurrounding("\"")
                }
                ".field" -> openField(tokens, line)
                ".method" -> openMethod(tokens, line)
                ".annotation" -> openAnnotation(if (field != null) AnnotationOwner.FIELD else AnnotationOwner.CLASS, tokens, line)
                ".end" -> {
                    val what = tokens.getOrNull(1)?.text
                    if (what == "field" && field != null) {
                        closeField(n)
                    } else {
                        closeField(null)
                        error(DiagnosticCode.UNEXPECTED_END, "`.end ${what ?: ""}` without a matching opening directive", n, first.column, tokens.last().endColumn)
                    }
                }
                ".registers", ".locals", ".param", ".line", ".local", ".restart", ".prologue", ".epilogue", ".catch",
                ".catchall", ".packed-switch", ".sparse-switch", ".array-data" ->
                    error(DiagnosticCode.OUTSIDE_METHOD, "`${first.text}` is only valid inside a method", n, first.column, first.endColumn)
                else -> unknownDirective(first, line)
            }
        }

        private fun unknownDirective(t: Token, line: LexedLine) {
            val names = DIRECTIVES.filter { !it.contains(' ') }
            val best = names.minByOrNull { DalvikOpcodeTable.editDistance(t.text, it) }
            val suggestion = best?.takeIf { DalvikOpcodeTable.editDistance(t.text, it) <= 2 }?.let { "Did you mean `$it`?" }
            error(DiagnosticCode.UNKNOWN_DIRECTIVE, "Unknown directive `${t.text}`", line.number, t.column, t.endColumn, suggestion)
        }

        private fun checkFlags(flagTokens: List<Token>, line: LexedLine) {
            flagTokens.forEach { t ->
                if (t.text !in ACCESS_FLAGS) {
                    error(DiagnosticCode.SYNTAX, "Unknown access flag `${t.text}`", line.number, t.column, t.endColumn)
                }
            }
        }

        private fun openField(tokens: List<Token>, line: LexedLine) {
            closeField(null)
            val n = line.number
            val eq = tokens.indexOfFirst { it.type == TokenType.EQUALS }
            val decl = if (eq >= 0) tokens.subList(1, eq) else tokens.drop(1)
            val nameToken = decl.lastOrNull()
            if (nameToken == null || !nameToken.text.contains(':')) {
                error(DiagnosticCode.SYNTAX, "Expected `name:Type` in field declaration", n, tokens[0].column, tokens.last().endColumn, "Example: .field private count:I")
                return
            }
            checkFlags(decl.dropLast(1), line)
            val name = nameToken.text.substringBefore(':')
            val type = nameToken.text.substringAfter(':')
            if (!Descriptors.isValidMemberName(name)) error(DiagnosticCode.SYNTAX, "Invalid field name `$name`", n, nameToken.column, nameToken.column + name.length)
            if (!Descriptors.isValidType(type)) {
                error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid field type `$type`", n, nameToken.column + name.length + 1, nameToken.endColumn, "Use a type descriptor such as I, J, Z or Ljava/lang/String;")
            }
            val value = if (eq >= 0) {
                val after = tokens.drop(eq + 1)
                if (after.isEmpty()) {
                    error(DiagnosticCode.SYNTAX, "Expected an initial value after `=`", n, tokens[eq].column, tokens[eq].endColumn)
                    null
                } else {
                    line.text.substring(after.first().column, after.last().endColumn)
                }
            } else {
                null
            }
            if (fields.any { it.name == name && it.type == type }) {
                error(DiagnosticCode.DUPLICATE_MEMBER, "Duplicate field `$name:$type`", n, nameToken.column, nameToken.endColumn)
            }
            field = FieldBuilder(decl.dropLast(1).map { it.text }, name, type, value, n, range(line, nameToken, nameToken))
        }

        private fun closeField(endLine: Int?) {
            val f = field ?: return
            field = null
            if (endLine == null && f.annotations.isNotEmpty()) {
                warning(DiagnosticCode.MISSING_END, "Field `${f.name}` has annotations but no `.end field`", f.line, f.nameRange.startColumn, f.nameRange.endColumn, "Add `.end field` after the field's annotations")
            }
            fields += f.build(endLine)
        }

        private fun openMethod(tokens: List<Token>, line: LexedLine) {
            closeField(null)
            val n = line.number
            val words = tokens.drop(1)
            val sig = words.lastOrNull()
            if (sig == null || !sig.text.contains('(')) {
                error(DiagnosticCode.SYNTAX, "Expected method name and prototype, e.g. `.method public foo(I)V`", n, tokens[0].column, tokens.last().endColumn)
                method = MethodBuilder(words.map { it.text }, sig?.text ?: "?", "", n, sig?.let { range(line, it, it) } ?: range(line, tokens[0], tokens[0]), false)
                return
            }
            checkFlags(words.dropLast(1), line)
            val name = sig.text.substringBefore('(')
            val proto = sig.text.substring(name.length)
            if (!Descriptors.isValidMemberName(name)) error(DiagnosticCode.SYNTAX, "Invalid method name `$name`", n, sig.column, sig.column + name.length)
            if (Descriptors.parseProto(proto) == null) {
                error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid method prototype `$proto`", n, sig.column + name.length, sig.endColumn, "Prototypes look like (ILjava/lang/String;)V")
            }
            if (methods.any { it.name == name && it.protoText == proto }) {
                error(DiagnosticCode.DUPLICATE_MEMBER, "Duplicate method `$name$proto`", n, sig.column, sig.endColumn)
            }
            method = MethodBuilder(words.dropLast(1).map { it.text }, name, proto, n, range(line, sig, sig), false)
        }

        private fun closeMethod(m: MethodBuilder, endLine: Int?) {
            closeParam(m)
            methods += m.build(endLine)
            method = null
        }

        private fun closeParam(m: MethodBuilder) {
            m.openParamIndex = -1
        }

        private fun handleMethodDirective(m: MethodBuilder, first: Token, tokens: List<Token>, line: LexedLine) {
            val n = line.number
            if (first.text != ".annotation" && first.text != ".end") closeParam(m)
            when (first.text) {
                ".end" -> {
                    when (val what = tokens.getOrNull(1)?.text) {
                        "method" -> if (m.implicit) {
                            error(DiagnosticCode.UNEXPECTED_END, "`.end method` without `.method`", n, first.column, tokens[1].endColumn)
                        } else {
                            closeMethod(m, n)
                        }
                        "local" -> m.items += DebugDirective(".end local", argsText(line, tokens, 2), n)
                        "param" -> {
                            val idx = m.openParamIndex
                            if (idx >= 0) {
                                val p = m.items[idx] as ParamDirective
                                m.items[idx] = p.copy(endLine = n)
                                m.openParamIndex = -1
                            } else {
                                error(DiagnosticCode.UNEXPECTED_END, "`.end param` without a matching `.param`", n, first.column, tokens.last().endColumn)
                            }
                        }
                        else -> error(DiagnosticCode.UNEXPECTED_END, "`.end ${what ?: ""}` without a matching opening directive", n, first.column, tokens.last().endColumn)
                    }
                }
                ".registers", ".locals" -> {
                    val v = tokens.getOrNull(1)
                    val value = v?.let { parseLiteral(it.text, range(line, it, it)) }?.value?.takeIf { it.signum() >= 0 && it.bitLength() <= 16 }?.toInt()
                    if (v == null || value == null) {
                        error(DiagnosticCode.SYNTAX, "`${first.text}` expects a register count between 0 and 65535", n, first.column, tokens.last().endColumn)
                    }
                    val directive = CountDirective(first.text, value, n, range(line, first, tokens.last()))
                    val existing = if (first.text == ".registers") m.registers else m.locals
                    if (existing != null) error(DiagnosticCode.DUPLICATE_DIRECTIVE, "Duplicate `${first.text}` directive", n, first.column, first.endColumn)
                    if (first.text == ".registers") m.registers = directive else m.locals = directive
                }
                ".param" -> {
                    val reg = tokens.getOrNull(1)?.takeIf { it.type == TokenType.REGISTER }
                    if (reg == null) error(DiagnosticCode.SYNTAX, "`.param` expects a parameter register, e.g. `.param p1, \"name\"`", n, first.column, tokens.last().endColumn)
                    val name = tokens.firstOrNull { it.type == TokenType.STRING }?.text?.removeSurrounding("\"")
                    m.items += ParamDirective(reg?.let { RegisterOperand(it.text, range(line, it, it)) }, name, n, null)
                    m.openParamIndex = m.items.size - 1
                }
                ".annotation" -> openAnnotation(AnnotationOwner.METHOD, tokens, line)
                ".catch", ".catchall" -> parseCatch(m, first, tokens, line)
                ".line", ".local", ".prologue", ".epilogue", ".source" -> m.items += DebugDirective(first.text, argsText(line, tokens, 1), n)
                ".restart" -> m.items += DebugDirective(".restart local", argsText(line, tokens, 2), n)
                ".packed-switch", ".sparse-switch", ".array-data" -> {
                    val kind = when (first.text) {
                        ".packed-switch" -> PayloadKind.PACKED_SWITCH
                        ".sparse-switch" -> PayloadKind.SPARSE_SWITCH
                        else -> PayloadKind.ARRAY_DATA
                    }
                    val headerToken = tokens.getOrNull(1)
                    val headerValue = headerToken?.let { parseLiteral(it.text, range(line, it, it)) }?.value?.toLong()
                    if (kind != PayloadKind.SPARSE_SWITCH && headerValue == null) {
                        val what = if (kind == PayloadKind.PACKED_SWITCH) "first key" else "element width"
                        error(DiagnosticCode.SYNTAX, "`${first.text}` expects the $what", n, first.column, tokens.last().endColumn)
                    }
                    if (kind == PayloadKind.ARRAY_DATA && headerValue != null && headerValue !in listOf(1L, 2L, 4L, 8L)) {
                        error(DiagnosticCode.SYNTAX, "Array element width must be 1, 2, 4 or 8 bytes", n, headerToken.column, headerToken.endColumn)
                    }
                    payload = PayloadBuilder(kind, n, m.labelsSinceItem.toList(), headerValue)
                    m.labelsSinceItem.clear()
                }
                ".method", ".field", ".class", ".super", ".implements" -> {
                    if (!m.implicit) {
                        error(DiagnosticCode.MISSING_END, "Method `${m.name}` is missing `.end method`", m.startLine, m.nameRange.startColumn, m.nameRange.endColumn, "Add `.end method` before line $n")
                        closeMethod(m, null)
                    } else {
                        methods += m.build(n - 1)
                        method = null
                    }
                    handleClassDirective(first, tokens, line)
                }
                else -> unknownDirective(first, line)
            }
        }

        private fun argsText(line: LexedLine, tokens: List<Token>, from: Int): String {
            val rest = tokens.drop(from)
            return if (rest.isEmpty()) "" else line.text.substring(rest.first().column, rest.last().endColumn)
        }

        private fun parseCatch(m: MethodBuilder, first: Token, tokens: List<Token>, line: LexedLine) {
            val n = line.number
            var i = 1
            var type: String? = null
            if (first.text == ".catch") {
                val t = tokens.getOrNull(1)
                if (t == null || t.type != TokenType.WORD) {
                    error(DiagnosticCode.SYNTAX, "`.catch` expects an exception type", n, first.column, first.endColumn); return
                }
                if (!Descriptors.isValidClassDescriptor(t.text)) error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid exception type `${t.text}`", n, t.column, t.endColumn)
                type = t.text
                i = 2
            }
            val rest = tokens.drop(i)
            val ok = rest.size == 6 && rest[0].type == TokenType.LBRACE && rest[1].type == TokenType.LABEL &&
                rest[2].type == TokenType.DOTDOT && rest[3].type == TokenType.LABEL && rest[4].type == TokenType.RBRACE &&
                rest[5].type == TokenType.LABEL
            if (!ok) {
                error(DiagnosticCode.SYNTAX, "Expected `{:start .. :end} :handler` after ${first.text}", n, first.column, tokens.last().endColumn, "Example: .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0")
                return
            }
            m.items += CatchDirective(
                type,
                LabelOperand(rest[1].text, range(line, rest[1], rest[1])),
                LabelOperand(rest[3].text, range(line, rest[3], rest[3])),
                LabelOperand(rest[5].text, range(line, rest[5], rest[5])),
                n, range(line, first, tokens.last()),
            )
            m.labelsSinceItem.clear()
        }

        // ----------------------------------------------------------------------------------------------------------
        // Instructions
        // ----------------------------------------------------------------------------------------------------------

        private fun handleInstruction(tokens: List<Token>, line: LexedLine) {
            val first = tokens[0]
            val n = line.number
            if (first.type != TokenType.WORD) {
                error(DiagnosticCode.SYNTAX, "Expected an instruction or directive, found `${first.text}`", n, first.column, first.endColumn)
                return
            }
            val m = method
            if (m != null) closeParam(m)
            val operands = parseOperands(tokens.drop(1), line)
            val text = line.text.substring(first.column, tokens.last().endColumn)
            val opcode = DalvikOpcodeTable.byMnemonic(first.text)
            val index = m?.instructionCount ?: strayIndex
            val insn = Instruction(first.text, opcode, operands, n, range(line, first, first), text, index)
            if (m == null) {
                error(DiagnosticCode.OUTSIDE_METHOD, "Instruction `${first.text}` is outside of a method", n, first.column, tokens.last().endColumn, "Move it between `.method` and `.end method`")
                stray += insn
                strayIndex++
            } else {
                m.items += insn
                m.instructionCount++
                m.labelsSinceItem.clear()
            }
        }

        private fun parseOperands(tokens: List<Token>, line: LexedLine): List<Operand> {
            if (tokens.isEmpty()) return emptyList()
            val groups = ArrayList<List<Token>>()
            var current = ArrayList<Token>()
            var depth = 0
            for (t in tokens) {
                when (t.type) {
                    TokenType.LBRACE -> depth++
                    TokenType.RBRACE -> depth--
                    TokenType.WORD -> depth += t.text.count { it == '(' } - t.text.count { it == ')' }
                    else -> Unit
                }
                if (t.type == TokenType.COMMA && depth <= 0) {
                    if (current.isEmpty()) {
                        error(DiagnosticCode.SYNTAX, "Unexpected comma", line.number, t.column, t.endColumn)
                    } else {
                        groups += current
                        current = ArrayList()
                    }
                    continue
                }
                current += t
            }
            if (current.isNotEmpty()) groups += current
            else error(DiagnosticCode.SYNTAX, "Trailing comma", line.number, tokens.last().column, tokens.last().endColumn)
            return groups.map { toOperand(it, line) }
        }

        private fun toOperand(group: List<Token>, line: LexedLine): Operand {
            val range = range(line, group.first(), group.last())
            val text = line.text.substring(range.startColumn, range.endColumn)
            val first = group.first()
            if (first.type == TokenType.LBRACE) {
                val closed = group.last().type == TokenType.RBRACE
                if (!closed) error(DiagnosticCode.SYNTAX, "Missing `}` in register list", line.number, range.startColumn, range.endColumn)
                val inner = group.drop(1).let { if (closed) it.dropLast(1) else it }
                if (inner.any { it.type == TokenType.DOTDOT }) {
                    if (inner.size == 3 && inner[0].type == TokenType.REGISTER && inner[1].type == TokenType.DOTDOT && inner[2].type == TokenType.REGISTER) {
                        return RegisterRangeOperand(text, range, RegisterOperand(inner[0].text, range(line, inner[0], inner[0])), RegisterOperand(inner[2].text, range(line, inner[2], inner[2])))
                    }
                    error(DiagnosticCode.RANGE_SYNTAX, "Register ranges must look like `{v0 .. v3}`", line.number, range.startColumn, range.endColumn)
                    return ReferenceOperand(text, range)
                }
                val regs = ArrayList<RegisterOperand>()
                var expectRegister = true
                for (t in inner) {
                    if (expectRegister && t.type == TokenType.REGISTER) {
                        regs += RegisterOperand(t.text, range(line, t, t)); expectRegister = false
                    } else if (!expectRegister && t.type == TokenType.COMMA) {
                        expectRegister = true
                    } else {
                        error(DiagnosticCode.SYNTAX, "Unexpected `${t.text}` in register list", line.number, t.column, t.endColumn, "Register lists look like {v0, v1, p0}")
                    }
                }
                if (expectRegister && regs.isNotEmpty()) error(DiagnosticCode.SYNTAX, "Trailing comma in register list", line.number, range.startColumn, range.endColumn)
                return RegisterListOperand(text, range, regs)
            }
            if (group.size == 1) {
                return when (first.type) {
                    TokenType.REGISTER -> RegisterOperand(text, range)
                    TokenType.LABEL -> LabelOperand(text, range)
                    TokenType.STRING -> StringOperand(text, range)
                    TokenType.NUMBER, TokenType.CHAR -> parseLiteral(text, range) ?: ReferenceOperand(text, range)
                    TokenType.WORD -> if (text == "true" || text == "false") {
                        LiteralOperand(text, range, LiteralType.BOOLEAN, if (text == "true") BigInteger.ONE else BigInteger.ZERO, false)
                    } else {
                        parseLiteral(text, range) ?: ReferenceOperand(text, range)
                    }
                    else -> ReferenceOperand(text, range)
                }
            }
            return ReferenceOperand(text, range)
        }

        private fun range(line: LexedLine, from: Token, to: Token) = SourceRange(line.number, from.column, to.endColumn)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Literals
    // -----------------------------------------------------------------------------------------------------------------

    /** Parses a smali literal (integer with optional L/S/T suffix, float/double, char, boolean). */
    fun parseLiteral(text: String, range: SourceRange): LiteralOperand? {
        if (text.isEmpty()) return null
        if (text == "true" || text == "false") {
            return LiteralOperand(text, range, LiteralType.BOOLEAN, if (text == "true") BigInteger.ONE else BigInteger.ZERO, false)
        }
        if (text.startsWith("'")) {
            val code = parseCharLiteral(text) ?: return null
            return LiteralOperand(text, range, LiteralType.CHAR, BigInteger.valueOf(code.toLong()), false)
        }
        if (SmaliLexer.isIntegerLiteral(text)) {
            var body = text
            val type = when (body.last()) {
                'L', 'l' -> LiteralType.LONG
                'S', 's' -> LiteralType.SHORT
                'T', 't' -> LiteralType.BYTE
                else -> LiteralType.INTEGER
            }
            if (type != LiteralType.INTEGER) body = body.dropLast(1)
            val negative = body.startsWith("-")
            if (negative) body = body.substring(1)
            val (radix, digits) = when {
                body.startsWith("0x") || body.startsWith("0X") -> 16 to body.substring(2)
                body.length > 1 && body.startsWith("0") -> 8 to body.substring(1)
                else -> 10 to body
            }
            val magnitude = runCatching { BigInteger(digits, radix) }.getOrNull() ?: return null
            val bits = when (type) {
                LiteralType.LONG -> 64
                LiteralType.SHORT -> 16
                LiteralType.BYTE -> 8
                else -> 32
            }
            val value = if (negative) magnitude.negate() else magnitude
            val min = BigInteger.ONE.shiftLeft(bits - 1).negate()
            val modulus = BigInteger.ONE.shiftLeft(bits)
            if (value < min || value >= modulus) return null
            val signed = if (value >= modulus.shiftRight(1)) value.subtract(modulus) else value
            return LiteralOperand(text, range, type, signed, radix != 10)
        }
        if (SmaliLexer.isFloatLiteral(text)) {
            val isFloat = text.endsWith("f") || text.endsWith("F")
            val core = text.trimEnd('f', 'F', 'd', 'D')
            val value = when (core.removePrefix("-")) {
                "Infinity" -> if (core.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
                "NaN" -> Double.NaN
                else -> core.toDoubleOrNull() ?: return null
            }
            return LiteralOperand(text, range, if (isFloat) LiteralType.FLOAT else LiteralType.DOUBLE, null, false, value)
        }
        return null
    }

    private fun parseCharLiteral(text: String): Int? {
        if (text.length < 3 || !text.endsWith("'")) return null
        val body = text.substring(1, text.length - 1)
        if (body.length == 1) return body[0].code
        if (!body.startsWith("\\")) return null
        return when (body) {
            "\\n" -> '\n'.code
            "\\t" -> '\t'.code
            "\\r" -> '\r'.code
            "\\b" -> '\b'.code
            "\\f" -> 0x0c
            "\\'" -> '\''.code
            "\\\"" -> '"'.code
            "\\\\" -> '\\'.code
            else -> if (body.startsWith("\\u") && body.length == 6) body.substring(2).toIntOrNull(16) else null
        }
    }
}
