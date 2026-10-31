package com.riyadm.smalihelperplus.core.smali.analysis

import com.riyadm.smalihelperplus.core.smali.ast.CatchDirective
import com.riyadm.smalihelperplus.core.smali.ast.Instruction
import com.riyadm.smalihelperplus.core.smali.ast.LabelOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralType
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
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
import com.riyadm.smalihelperplus.core.smali.diagnostics.Diagnostic
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity
import com.riyadm.smalihelperplus.core.smali.model.Descriptors
import com.riyadm.smalihelperplus.core.smali.opcodes.DalvikOpcode
import com.riyadm.smalihelperplus.core.smali.opcodes.DalvikOpcodeTable
import com.riyadm.smalihelperplus.core.smali.opcodes.FlowKind
import com.riyadm.smalihelperplus.core.smali.opcodes.LiteralKind
import com.riyadm.smalihelperplus.core.smali.opcodes.OpcodeFamily
import com.riyadm.smalihelperplus.core.smali.opcodes.OperandSpec
import com.riyadm.smalihelperplus.core.smali.opcodes.ReferenceKind
import java.math.BigInteger

/**
 * Semantic checks on a parsed [SmaliFile]. This is static syntax/structure validation only: nothing is assembled or
 * executed.
 */
class SmaliValidator(private val minApi: Int = 26) {
    private val out = ArrayList<Diagnostic>()

    fun validate(file: SmaliFile): List<Diagnostic> {
        out.clear()
        if (!file.isFragment) {
            if (file.header == null) {
                add(Severity.ERROR, DiagnosticCode.MISSING_CLASS, "Missing `.class` directive", 1, suggestion = "Start the file with e.g. `.class public Lcom/acme/demo/MyClass;`")
            }
            if (file.superClass == null && file.header?.descriptor != "Ljava/lang/Object;") {
                add(Severity.ERROR, DiagnosticCode.MISSING_SUPER, "Missing `.super` directive", file.header?.line ?: 1, suggestion = "Add `.super Ljava/lang/Object;` after `.class`")
            }
        }
        file.methods.forEach { validateMethod(file, it) }
        return out.toList()
    }

    private fun add(
        severity: Severity,
        code: DiagnosticCode,
        message: String,
        line: Int,
        range: SourceRange? = null,
        suggestion: String? = null,
    ) {
        out += Diagnostic(severity, code, message, line, range?.startColumn ?: 0, range?.endColumn ?: 0, suggestion)
    }

    private fun error(code: DiagnosticCode, message: String, range: SourceRange, suggestion: String? = null) =
        add(Severity.ERROR, code, message, range.line, range, suggestion)

    private fun warning(code: DiagnosticCode, message: String, range: SourceRange, suggestion: String? = null) =
        add(Severity.WARNING, code, message, range.line, range, suggestion)

    // -----------------------------------------------------------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------------------------------------------------------

    private fun validateMethod(file: SmaliFile, method: MethodDefinition) {
        val frame = RegisterFrame(method, file.className)
        val layout = MethodLayout(method)
        val insns = method.instructions

        if (!method.isImplicit) {
            if (method.registers != null && method.locals != null) {
                error(DiagnosticCode.REGISTERS_AND_LOCALS, "A method may declare either `.registers` or `.locals`, not both", method.locals.range, "Remove one of them (.locals counts only non-parameter registers)")
            }
            val pw = method.parameterWords
            val regs = method.registers?.value
            if (regs != null && pw != null && regs < pw) {
                error(
                    DiagnosticCode.REGISTER_OUT_OF_FRAME,
                    "`.registers $regs` is smaller than the $pw register(s) needed for the parameters${if (method.isStatic) "" else " (including this)"}",
                    method.registers.range, "Use at least `.registers $pw`, or declare `.locals` instead",
                )
            }
            if (method.isAbstractOrNative && insns.isNotEmpty()) {
                error(DiagnosticCode.CODE_IN_ABSTRACT, "Abstract and native methods cannot contain instructions", method.nameRange)
            }
            if (!method.isAbstractOrNative && insns.isEmpty() && method.endLine != null) {
                error(DiagnosticCode.MISSING_CODE, "Method `${method.name}` has no instructions", method.nameRange, "Non-abstract, non-native methods need code, e.g. `return-void`")
            }
            if (insns.isNotEmpty() && method.registers == null && method.locals == null) {
                error(DiagnosticCode.MISSING_REGISTERS, "Method `${method.name}` has instructions but no `.registers` or `.locals` directive", method.nameRange, "Add `.locals N` as the first line of the method body")
            }
        }

        if ((frame.size ?: 0) > 65535) {
            error(DiagnosticCode.REGISTER_OUT_OF_FRAME, "The register frame including parameters exceeds 65535 registers", method.nameRange)
        }
        validateLabels(method, layout)
        method.items.filterIsInstance<ParamDirective>().forEach { p ->
            val r = p.register ?: return@forEach
            val pw = frame.parameterWords
            if (!r.isParameter) {
                warning(DiagnosticCode.OPERAND_KIND, "`.param` normally names a parameter register (pN)", r.range)
            } else if (pw != null && r.number >= pw) {
                error(DiagnosticCode.PARAMETER_OUT_OF_RANGE, "`${r.text}` does not exist: this method has $pw parameter register(s)", r.range)
            }
        }

        insns.forEach { insn -> validateInstruction(method, frame, layout, insn) }

        if (!method.isImplicit && insns.isNotEmpty()) {
            val last = insns.last()
            val flow = last.opcode?.flow
            if (flow != null && flow != FlowKind.RETURN && flow != FlowKind.GOTO && flow != FlowKind.THROW) {
                error(DiagnosticCode.FALLS_OFF_END, "Execution can fall off the end of the method after `${last.mnemonic}`", last.range, "End the method with a return, goto or throw")
            }
        }
    }

    private fun validateLabels(method: MethodDefinition, layout: MethodLayout) {
        layout.duplicateLabels.forEach {
            error(DiagnosticCode.DUPLICATE_LABEL, "Label `:${it.name}` is defined more than once", it.range, "Rename one of the labels")
        }
        val referenced = HashSet<String>()
        fun check(label: LabelOperand) {
            referenced += label.name
            if (!layout.labelTargets.containsKey(label.name)) {
                val best = layout.labelDefinitions.keys.minByOrNull { DalvikOpcodeTable.editDistance(label.name, it) }
                val suggestion = best?.takeIf { DalvikOpcodeTable.editDistance(label.name, it) <= maxOf(2, label.name.length / 3) }?.let { "Did you mean `:$it`?" }
                error(DiagnosticCode.UNDEFINED_LABEL, "Label `${label.text}` is not defined in this method", label.range, suggestion)
            }
        }
        method.items.forEach { item ->
            when (item) {
                is Instruction -> item.operands.filterIsInstance<LabelOperand>().forEach(::check)
                is CatchDirective -> {
                    check(item.start); check(item.end); check(item.handler)
                    val start = layout.codeIndex(item.start.name)
                    val end = layout.boundaryIndex(item.end.name)
                    val handler = layout.codeIndex(item.handler.name)
                    if (listOf(item.start, item.end, item.handler).all { it.name in layout.labelTargets } &&
                        (start == null || end == null || start >= end || handler == null || handler >= layout.instructions.size)) {
                        error(DiagnosticCode.RANGE_SYNTAX, "Catch ranges must cover instructions in forward order and have an instruction handler", item.range)
                    }
                }
                is PayloadDirective -> item.targets.forEach { target ->
                    check(target)
                    if (target.name in layout.labelTargets &&
                        (layout.codeIndex(target.name) ?: layout.instructions.size) >= layout.instructions.size) {
                        error(DiagnosticCode.OPERAND_KIND, "Switch case target must label an instruction", target.range)
                    }
                }
                else -> Unit
            }
        }
        if (!method.isImplicit) {
            layout.labelDefinitions.values.filter { it.name !in referenced }.forEach {
                add(Severity.INFO, DiagnosticCode.UNUSED_LABEL, "Label `:${it.name}` is never referenced", it.line, it.range)
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Instructions
    // -----------------------------------------------------------------------------------------------------------------

    private fun validateInstruction(method: MethodDefinition, frame: RegisterFrame, layout: MethodLayout, insn: Instruction) {
        val op = insn.opcode
        if (op == null) {
            val suggestions = DalvikOpcodeTable.suggest(insn.mnemonic)
            error(
                DiagnosticCode.UNKNOWN_OPCODE, "Unknown instruction `${insn.mnemonic}`", insn.mnemonicRange,
                suggestions.takeIf { it.isNotEmpty() }?.let { s -> "Did you mean ${s.joinToString(" or ") { "`$it`" }}?" },
            )
            return
        }
        if (op.minApi > minApi) {
            warning(
                DiagnosticCode.API_LEVEL,
                "`${op.mnemonic}` requires Android API ${op.minApi}+ (dex ${if (op.minApi >= 28) "039" else "038"}), but the target minimum is API $minApi",
                insn.mnemonicRange, "Raise minSdk to ${op.minApi} or avoid this instruction",
            )
        }
        val specs = op.operands
        if (insn.operands.size != specs.size) {
            error(
                DiagnosticCode.OPERAND_COUNT,
                "`${op.mnemonic}` expects ${specs.size} operand${if (specs.size == 1) "" else "s"} (format ${op.format.id}: ${op.format.syntax}), found ${insn.operands.size}",
                insn.range,
            )
        }
        specs.zip(insn.operands).forEach { (spec, operand) -> checkOperand(method, frame, layout, insn, op, spec, operand) }
        checkSemantics(method, frame, layout, insn, op)
    }

    private fun kindError(insn: Instruction, operand: Operand, expected: String) {
        error(DiagnosticCode.OPERAND_KIND, "`${insn.mnemonic}` expects $expected here, found `${operand.text}`", operand.range)
    }

    private fun checkOperand(
        method: MethodDefinition,
        frame: RegisterFrame,
        layout: MethodLayout,
        insn: Instruction,
        op: DalvikOpcode,
        spec: OperandSpec,
        operand: Operand,
    ) {
        when (spec) {
            is OperandSpec.Register -> {
                if (operand !is RegisterOperand) return kindError(insn, operand, "a register")
                checkRegister(method, frame, insn, op, operand, spec.bits, spec.wide)
            }
            is OperandSpec.Literal -> {
                if (operand !is LiteralOperand) return kindError(insn, operand, "a literal value")
                checkLiteral(insn, op, spec, operand)
            }
            is OperandSpec.Reference -> checkReference(insn, op, spec.kind, operand)
            is OperandSpec.BranchTarget -> if (operand !is LabelOperand) {
                kindError(insn, operand, "a label such as :cond_0")
            } else if (layout.labelTargets.containsKey(operand.name) &&
                (layout.codeIndex(operand.name) ?: layout.instructions.size) >= layout.instructions.size) {
                error(DiagnosticCode.OPERAND_KIND, "Branch target must label an instruction", operand.range)
            }
            is OperandSpec.PayloadTarget -> {
                if (operand !is LabelOperand) return kindError(insn, operand, "a payload label")
                val target = layout.labelTargets[operand.name]
                if (target != null) {
                    val payload = (target as? LabelTarget.Payload)?.payload
                    if (payload == null || payload.kind != spec.kind) {
                        error(
                            DiagnosticCode.PAYLOAD_MISMATCH,
                            "`${operand.text}` must label a `${spec.kind.directive}` payload",
                            operand.range,
                        )
                    }
                }
            }
            is OperandSpec.RegisterList -> {
                when (operand) {
                    is RegisterRangeOperand -> error(DiagnosticCode.RANGE_SYNTAX, "`${op.mnemonic}` takes a register list like {v0, v1}; register ranges need the /range variant", operand.range, rangeVariant(op)?.let { "Use `$it`" })
                    is RegisterListOperand -> {
                        if (operand.registers.size > spec.max) {
                            error(
                                DiagnosticCode.TOO_MANY_REGISTERS,
                                "`${op.mnemonic}` can pass at most ${spec.max} registers, found ${operand.registers.size}",
                                operand.range,
                                rangeVariant(op)?.let { "Use `$it` with contiguous registers, e.g. {v0 .. v${operand.registers.size - 1}}" },
                            )
                        }
                        operand.registers.forEach { checkRegister(method, frame, insn, op, it, 4, false, listMode = true) }
                    }
                    else -> kindError(insn, operand, "a register list such as {v0, v1}")
                }
            }
            OperandSpec.RegisterRange -> {
                when (operand) {
                    is RegisterRangeOperand -> {
                        val a = frame.index(operand.first)
                        val b = frame.index(operand.last)
                        if (operand.first.prefix != operand.last.prefix && !frame.isKnown) {
                            error(DiagnosticCode.RANGE_SYNTAX, "Both ends of a register range must use the same register prefix", operand.range)
                        } else if (a != null && b != null) {
                            if (b < a) {
                                error(DiagnosticCode.RANGE_SYNTAX, "Register range `${operand.text}` must go from the lower to the higher register", operand.range)
                            } else if (b - a + 1 > 255) {
                                error(DiagnosticCode.TOO_MANY_REGISTERS, "A register range can contain at most 255 registers", operand.range)
                            }
                        }
                        checkRegister(method, frame, insn, op, operand.first, 16, false)
                        checkRegister(method, frame, insn, op, operand.last, 16, false)
                    }
                    is RegisterListOperand -> if (operand.registers.size > 1) {
                        error(DiagnosticCode.RANGE_SYNTAX, "`${op.mnemonic}` takes a contiguous range like {v0 .. v3}, not a list", operand.range)
                    } else {
                        operand.registers.forEach { checkRegister(method, frame, insn, op, it, 16, false) }
                    }
                    else -> kindError(insn, operand, "a register range such as {v0 .. v3}")
                }
            }
        }
    }

    private fun rangeVariant(op: DalvikOpcode): String? = DalvikOpcodeTable.byMnemonic(op.mnemonic + "/range")?.mnemonic

    private fun checkRegister(
        method: MethodDefinition,
        frame: RegisterFrame,
        insn: Instruction,
        op: DalvikOpcode,
        reg: RegisterOperand,
        bits: Int,
        wide: Boolean,
        listMode: Boolean = false,
    ) {
        if (reg.number == Int.MAX_VALUE) {
            error(DiagnosticCode.OPERAND_KIND, "Register number `${reg.text}` is too large", reg.range)
            return
        }
        val pw = frame.parameterWords
        if (reg.isParameter && pw != null && reg.number >= pw) {
            error(
                DiagnosticCode.PARAMETER_OUT_OF_RANGE,
                "`${reg.text}` does not exist: `${method.name}` has $pw parameter register(s) (p0..p${pw - 1})${if (!method.isStatic) ", p0 is this" else ""}",
                reg.range,
            )
            return
        }
        val index = frame.index(reg) ?: return
        val size = frame.size
        if (size != null && index >= size) {
            error(
                DiagnosticCode.REGISTER_OUT_OF_FRAME,
                "`${reg.text}` is outside the method's register frame (${size} register${if (size == 1) "" else "s"}: v0..v${size - 1})",
                reg.range,
                "Increase `.locals`/`.registers` or use a lower register",
            )
            return
        }
        val max = (1 shl bits) - 1
        if (index > max) {
            val alias = if (reg.isParameter) " (`${reg.text}` is v$index in this frame)" else ""
            val suggestion = when {
                listMode -> rangeVariant(op)?.let { "Use `$it` with a contiguous range, or move the value into v0–v15 first" }
                    ?: "Move the value into v0–v15 first"
                else -> widerVariant(op, bits) ?: when (bits) {
                    4 -> "Move the value into one of v0–v15 first (e.g. with move/from16 or move-object/from16)"
                    8 -> "Move the value into one of v0–v255 first (e.g. with move/16 or move-object/16)"
                    else -> null
                }
            }
            error(
                DiagnosticCode.REGISTER_WIDTH,
                "`${op.mnemonic}` (format ${op.format.id}) can only address v0–v$max in this position$alias",
                reg.range, suggestion,
            )
        }
        if (wide && size != null && index + 1 >= size) {
            error(
                DiagnosticCode.WIDE_PAIR_OVERFLOW,
                "`${reg.text}` holds a 64-bit value and needs the pair v$index/v${index + 1}, but the frame ends at v${size - 1}",
                reg.range, "Use a lower register or increase the register count",
            )
        }
    }

    /** For move-family instructions suggests the /from16 or /16 variant that can address the register. */
    private fun widerVariant(op: DalvikOpcode, bits: Int): String? {
        val base = op.mnemonic.substringBefore('/')
        if (op.family != OpcodeFamily.MOVE) return null
        return when (bits) {
            4 -> "Use `$base/from16` (8-bit destination, 16-bit source) or `$base/16`"
            8 -> "Use `$base/16` (16-bit registers)"
            else -> null
        }
    }

    private fun checkLiteral(insn: Instruction, op: DalvikOpcode, spec: OperandSpec.Literal, lit: LiteralOperand) {
        val kindName = when (spec.kind) {
            LiteralKind.SIGNED -> "a signed ${spec.bits}-bit integer"
            LiteralKind.ARBITRARY -> "a ${spec.bits}-bit constant"
            LiteralKind.HIGH16 -> "a 32-bit value whose low 16 bits are zero"
            LiteralKind.WIDE_HIGH16 -> "a 64-bit value whose low 48 bits are zero"
        }
        if (spec.kind == LiteralKind.SIGNED && lit.type == LiteralType.BOOLEAN) {
            error(DiagnosticCode.OPERAND_KIND, "`${op.mnemonic}` expects an integer literal", lit.range)
            return
        }
        if (lit.isFloatingPoint) {
            when (spec.kind) {
                LiteralKind.SIGNED -> {
                    error(DiagnosticCode.OPERAND_KIND, "`${op.mnemonic}` expects an integer literal, found `${lit.text}`", lit.range, "Write the raw bits as hex, or use `const`/`const-wide` for floating point values")
                    return
                }
                LiteralKind.ARBITRARY, LiteralKind.HIGH16 -> if (spec.bits == 32 && lit.type != LiteralType.FLOAT) {
                    error(DiagnosticCode.OPERAND_KIND, "`${op.mnemonic}` holds a 32-bit value; `${lit.text}` is a double literal", lit.range, "Add an `f` suffix (e.g. ${lit.text}f) or use `const-wide`")
                    return
                }
                LiteralKind.WIDE_HIGH16 -> Unit
            }
            val bits: BigInteger = when {
                spec.bits == 32 -> BigInteger.valueOf(java.lang.Float.floatToRawIntBits(lit.floatValue!!.toFloat()).toLong() and 0xffffffffL)
                lit.type == LiteralType.FLOAT -> BigInteger.valueOf(java.lang.Float.floatToRawIntBits(lit.floatValue!!.toFloat()).toLong())
                else -> BigInteger.valueOf(java.lang.Double.doubleToRawLongBits(lit.floatValue!!))
            }
            checkHighBits(op, spec, bits, lit)
            return
        }
        val raw = lit.value ?: return
        val value = if (spec.kind == LiteralKind.SIGNED && lit.type == LiteralType.CHAR) {
            BigInteger.valueOf(raw.toShort().toLong())
        } else raw
        val b = spec.bits
        val min = BigInteger.ONE.shiftLeft(b - 1).negate()
        val maxSigned = BigInteger.ONE.shiftLeft(b - 1).subtract(BigInteger.ONE)
        val maxUnsigned = BigInteger.ONE.shiftLeft(b).subtract(BigInteger.ONE)
        val max = if (b > 4) maxUnsigned else maxSigned
        if (value < min || value > max) {
            error(
                DiagnosticCode.LITERAL_RANGE,
                "`${lit.text}` does not fit in `${op.mnemonic}`, which takes $kindName (${min}..${maxSigned})",
                lit.range, literalSuggestion(op, value),
            )
            return
        }
        checkHighBits(op, spec, value, lit)
    }

    private fun checkHighBits(op: DalvikOpcode, spec: OperandSpec.Literal, value: BigInteger, lit: LiteralOperand) {
        val pattern = value.toLong()
        when (spec.kind) {
            LiteralKind.HIGH16 -> if (pattern and 0xffffL != 0L) {
                error(DiagnosticCode.LITERAL_RANGE, "`const/high16` only stores the high 16 bits; the low 16 bits of `${lit.text}` must be zero", lit.range, "Use `const` for arbitrary 32-bit values")
            }
            LiteralKind.WIDE_HIGH16 -> if (pattern and 0xffffffffffffL != 0L) {
                error(DiagnosticCode.LITERAL_RANGE, "`const-wide/high16` only stores the high 16 bits; the low 48 bits of `${lit.text}` must be zero", lit.range, "Use `const-wide` for arbitrary 64-bit values")
            }
            else -> Unit
        }
    }

    private fun fits(value: BigInteger, bits: Int): Boolean =
        value >= BigInteger.ONE.shiftLeft(bits - 1).negate() && value <= BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE)

    private fun literalSuggestion(op: DalvikOpcode, value: BigInteger): String? = when (op.mnemonic) {
        "const/4" -> when {
            fits(value, 16) -> "Use `const/16` for values from -32768 to 32767"
            fits(value, 32) && value.toLong() and 0xffffL == 0L -> "Use `const/high16` or `const`"
            else -> "Use `const` for 32-bit values"
        }
        "const/16" -> "Use `const` for 32-bit values"
        "const-wide/16" -> if (fits(value, 32)) "Use `const-wide/32`" else "Use `const-wide`"
        "const-wide/32" -> "Use `const-wide` for 64-bit values"
        else -> when (op.family) {
            OpcodeFamily.BINARY_LIT8 -> {
                val lit16 = op.mnemonic.replace("/lit8", "/lit16").let { if (it == "rsub-int/lit16") "rsub-int" else it }
                if (DalvikOpcodeTable.byMnemonic(lit16) != null && fits(value, 16)) "Use `$lit16` for 16-bit literals"
                else "Load the value into a register with `const` and use the register form `${op.mnemonic.substringBefore('/')}`"
            }
            OpcodeFamily.BINARY_LIT16 -> "Load the value into a register with `const` and use `${op.mnemonic.substringBefore('/').let { if (it == "rsub-int") "sub-int" else it }}`"
            else -> null
        }
    }

    private fun checkReference(insn: Instruction, op: DalvikOpcode, kind: ReferenceKind, operand: Operand) {
        val text = operand.text
        when (kind) {
            ReferenceKind.STRING -> if (operand !is StringOperand) kindError(insn, operand, "a string literal")
            ReferenceKind.TYPE -> {
                if (operand !is ReferenceOperand) return kindError(insn, operand, "a type descriptor")
                if (!Descriptors.isValidType(text)) {
                    return error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid type descriptor `$text`", operand.range, "Examples: I, [I, Ljava/lang/String;")
                }
                when (op.family) {
                    OpcodeFamily.NEW_INSTANCE -> if (!text.startsWith("L")) error(DiagnosticCode.INVALID_DESCRIPTOR, "`new-instance` needs a class type, not `$text`", operand.range, "Use new-array for arrays")
                    OpcodeFamily.NEW_ARRAY -> if (!text.startsWith("[")) error(DiagnosticCode.INVALID_DESCRIPTOR, "`${op.mnemonic}` needs an array type such as [I, found `$text`", operand.range)
                    OpcodeFamily.FILLED_NEW_ARRAY -> when {
                        !text.startsWith("[") -> error(DiagnosticCode.INVALID_DESCRIPTOR, "`${op.mnemonic}` needs an array type such as [I, found `$text`", operand.range)
                        text == "[J" || text == "[D" -> error(DiagnosticCode.INVALID_DESCRIPTOR, "`${op.mnemonic}` cannot build long or double arrays", operand.range, "Use new-array and fill-array-data or aput-wide")
                    }
                    OpcodeFamily.CHECK_CAST, OpcodeFamily.INSTANCE_OF -> if (!Descriptors.isReference(text)) {
                        error(DiagnosticCode.INVALID_DESCRIPTOR, "`${op.mnemonic}` needs a reference type, found primitive `$text`", operand.range)
                    }
                    else -> Unit
                }
            }
            ReferenceKind.FIELD -> {
                if (operand !is ReferenceOperand) return kindError(insn, operand, "a field reference")
                val ref = Descriptors.parseFieldReference(text)
                if (ref == null) {
                    error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid field reference `$text`", operand.range, "Field references look like Lcom/acme/demo/Foo;->count:I")
                } else {
                    val wideOp = op.mnemonic.endsWith("-wide")
                    val objOp = op.mnemonic.endsWith("-object")
                    val expected = op.mnemonic.substringAfter('-', "")
                    val mismatch = when {
                        wideOp -> !Descriptors.isWide(ref.type)
                        objOp -> !Descriptors.isReference(ref.type)
                        expected == "boolean" -> ref.type != "Z"
                        expected == "byte" -> ref.type != "B"
                        expected == "char" -> ref.type != "C"
                        expected == "short" -> ref.type != "S"
                        else -> ref.type !in setOf("I", "F")
                    }
                    if (mismatch) {
                        warning(DiagnosticCode.OPERAND_KIND, "`${op.mnemonic}` does not match the field type `${ref.type}`", operand.range, fieldOpSuggestion(op, ref.type))
                    }
                }
            }
            ReferenceKind.METHOD -> {
                if (operand !is ReferenceOperand) return kindError(insn, operand, "a method reference")
                if (Descriptors.parseMethodReference(text) == null) {
                    error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid method reference `$text`", operand.range, "Method references look like Lcom/acme/demo/Foo;->bar(ILjava/lang/String;)V")
                }
            }
            ReferenceKind.PROTO -> {
                if (operand !is ReferenceOperand) return kindError(insn, operand, "a method prototype")
                if (Descriptors.parseProto(text) == null) error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid method prototype `$text`", operand.range, "Prototypes look like (II)V")
            }
            ReferenceKind.METHOD_HANDLE -> {
                if (operand !is ReferenceOperand) return kindError(insn, operand, "a method handle")
                val type = text.substringBefore('@', "")
                val target = text.substringAfter('@', "")
                val types = setOf("static-put", "static-get", "instance-put", "instance-get", "invoke-static", "invoke-instance", "invoke-constructor", "invoke-direct", "invoke-interface")
                val valid = type in types && if (type.endsWith("-put") || type.endsWith("-get")) Descriptors.parseFieldReference(target) != null else Descriptors.parseMethodReference(target) != null
                if (!valid) error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid method handle `$text`", operand.range, "Method handles look like invoke-static@Lcom/acme/demo/Foo;->bar()V")
            }
            ReferenceKind.CALL_SITE -> {
                if (operand !is ReferenceOperand || !text.contains('(') || !text.contains('@') || !text.contains("->")) {
                    error(DiagnosticCode.INVALID_DESCRIPTOR, "Invalid call site reference `$text`", operand.range, "Call sites look like call_site_0(\"name\", (I)V)@Lcom/acme/demo/Bootstrap;->bsm(...)Ljava/lang/invoke/CallSite;")
                }
            }
        }
    }

    private fun fieldOpSuggestion(op: DalvikOpcode, type: String): String {
        val base = op.mnemonic.substringBefore('-')
        val suffix = when {
            Descriptors.isWide(type) -> "-wide"
            Descriptors.isReference(type) -> "-object"
            type == "Z" -> "-boolean"
            type == "B" -> "-byte"
            type == "C" -> "-char"
            type == "S" -> "-short"
            else -> ""
        }
        return "Use `$base$suffix` for a field of type $type"
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Cross-instruction rules
    // -----------------------------------------------------------------------------------------------------------------

    private fun checkSemantics(method: MethodDefinition, frame: RegisterFrame, layout: MethodLayout, insn: Instruction, op: DalvikOpcode) {
        // Return opcode vs. declared return type.
        val ret = method.proto?.returnType
        if (op.family == OpcodeFamily.RETURN && ret != null && !method.isImplicit) {
            val expected = when {
                ret == "V" -> "return-void"
                Descriptors.isWide(ret) -> "return-wide"
                Descriptors.isReference(ret) -> "return-object"
                else -> "return"
            }
            if (op.mnemonic != expected) {
                error(
                    DiagnosticCode.RETURN_MISMATCH,
                    "`${op.mnemonic}` does not match the return type ${Descriptors.toJavaName(ret)} of `${method.name}`",
                    insn.mnemonicRange, "Use `$expected`",
                )
            }
        }

        // Argument word count of invokes.
        if (op.isInvoke) checkInvokeArguments(frame, insn, op)

        // move-result* placement.
        if (op.family == OpcodeFamily.MOVE_RESULT) {
            val prev = layout.instructions.getOrNull(insn.index - 1)
            val prevOp = prev?.opcode
            if (prev == null || prevOp == null || !prevOp.setsResult) {
                error(
                    DiagnosticCode.MOVE_RESULT_PLACEMENT,
                    "`${op.mnemonic}` must come immediately after an invoke-kind or filled-new-array instruction",
                    insn.mnemonicRange,
                )
            } else {
                if (layout.labelsAt.containsKey(insn.index)) {
                    warning(DiagnosticCode.MOVE_RESULT_PLACEMENT, "A label between `${prev.mnemonic}` and `${op.mnemonic}` makes it a branch target, which the verifier rejects", insn.mnemonicRange)
                }
                val resultType: String? = when (prevOp.family) {
                    OpcodeFamily.FILLED_NEW_ARRAY -> "[" // always an object
                    OpcodeFamily.INVOKE -> prev.operands.getOrNull(1)?.let { Descriptors.parseMethodReference(it.text)?.proto?.returnType }
                    OpcodeFamily.INVOKE_POLYMORPHIC -> prev.operands.getOrNull(2)?.let { Descriptors.parseProto(it.text)?.returnType }
                    else -> null
                }
                if (resultType != null) {
                    val expected = when {
                        resultType == "V" -> null
                        Descriptors.isWide(resultType) -> "move-result-wide"
                        Descriptors.isReference(resultType) -> "move-result-object"
                        else -> "move-result"
                    }
                    if (expected == null) {
                        error(DiagnosticCode.MOVE_RESULT_PLACEMENT, "`${prev.mnemonic}` calls a void method, so there is no result to move", insn.mnemonicRange, "Remove this instruction")
                    } else if (expected != op.mnemonic) {
                        error(DiagnosticCode.MOVE_RESULT_PLACEMENT, "The previous call returns ${Descriptors.toJavaName(resultType)}; use `$expected`", insn.mnemonicRange, "Use `$expected`")
                    }
                }
            }
        }

        if (op.family == OpcodeFamily.MOVE_EXCEPTION && !method.isImplicit && insn.index !in layout.handlerIndices) {
            warning(DiagnosticCode.MOVE_EXCEPTION_PLACEMENT, "`move-exception` should be the first instruction of a catch handler", insn.mnemonicRange)
        }
    }

    private fun checkInvokeArguments(frame: RegisterFrame, insn: Instruction, op: DalvikOpcode) {
        val regsOperand = insn.operands.firstOrNull() ?: return
        val words: Int = when (regsOperand) {
            is RegisterListOperand -> regsOperand.registers.size
            is RegisterRangeOperand -> {
                val a = frame.index(regsOperand.first) ?: return
                val b = frame.index(regsOperand.last) ?: return
                if (b < a) return
                b - a + 1
            }
            else -> return
        }
        val (expected, description) = when (op.family) {
            OpcodeFamily.INVOKE -> {
                val ref = insn.operands.getOrNull(1)?.let { Descriptors.parseMethodReference(it.text) } ?: return
                val isStatic = op.mnemonic.startsWith("invoke-static")
                val n = ref.proto.parameterWords + if (isStatic) 0 else 1
                n to "${ref.owner}->${ref.name}${ref.proto.descriptor}${if (isStatic) "" else " (including the receiver)"}"
            }
            OpcodeFamily.INVOKE_POLYMORPHIC -> {
                val proto = insn.operands.getOrNull(2)?.let { Descriptors.parseProto(it.text) } ?: return
                (proto.parameterWords + 1) to "the prototype ${proto.descriptor} plus the method handle receiver"
            }
            else -> return
        }
        if (words != expected) {
            error(
                DiagnosticCode.ARGUMENT_COUNT,
                "`${op.mnemonic}` passes $words register word${if (words == 1) "" else "s"}, but $description needs $expected",
                regsOperand.range, "long and double arguments take two consecutive registers",
            )
            return
        }
        // Wide arguments in an explicit list must be consecutive register pairs.
        if (regsOperand is RegisterListOperand) {
            val parameters = when (op.family) {
                OpcodeFamily.INVOKE -> Descriptors.parseMethodReference(insn.operands[1].text)?.proto?.parameters
                OpcodeFamily.INVOKE_POLYMORPHIC -> Descriptors.parseProto(insn.operands[2].text)?.parameters
                else -> null
            } ?: return
            var pos = if (op.mnemonic.startsWith("invoke-static")) 0 else 1
            for (type in parameters) {
                if (Descriptors.isWide(type)) {
                    val lo = regsOperand.registers.getOrNull(pos)
                    val hi = regsOperand.registers.getOrNull(pos + 1)
                    if (lo != null && hi != null) {
                        val li = frame.index(lo)
                        val hiIdx = frame.index(hi)
                        if (li != null && hiIdx != null && hiIdx != li + 1) {
                            error(DiagnosticCode.ARGUMENT_COUNT, "The ${Descriptors.toJavaName(type)} argument must use a consecutive register pair, e.g. `${lo.text}, ${lo.prefix}${lo.number + 1}`", hi.range)
                        }
                    }
                    pos += 2
                } else {
                    pos += 1
                }
            }
        }
    }
}
