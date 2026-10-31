package com.riyadm.smalihelperplus.core.smali.explain

import com.riyadm.smalihelperplus.core.smali.analysis.ControlFlowAnalyzer
import com.riyadm.smalihelperplus.core.smali.analysis.MethodLayout
import com.riyadm.smalihelperplus.core.smali.analysis.RegKey
import com.riyadm.smalihelperplus.core.smali.analysis.RegisterAnalyzer
import com.riyadm.smalihelperplus.core.smali.analysis.RegisterFrame
import com.riyadm.smalihelperplus.core.smali.ast.Instruction
import com.riyadm.smalihelperplus.core.smali.ast.LabelOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralType
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.Operand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterListOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterRangeOperand
import com.riyadm.smalihelperplus.core.smali.ast.SmaliFile
import com.riyadm.smalihelperplus.core.smali.ast.StringOperand
import com.riyadm.smalihelperplus.core.smali.model.Descriptors
import com.riyadm.smalihelperplus.core.smali.opcodes.OpcodeFamily
import com.riyadm.smalihelperplus.core.smali.opcodes.OperandSpec
import com.riyadm.smalihelperplus.core.smali.structure.StructureAnalyzer
import java.math.BigInteger

/**
 * Translates a method into **educational** Java-style pseudocode. Only well-understood patterns are translated;
 * anything else is emitted as an explicit `/* untranslated: ... */` line. This is not a decompiler: register names are
 * kept, control flow uses `goto` unless a simple if/else shape is recognised, and no types are inferred beyond what the
 * instructions state.
 */
object PseudocodeGenerator {
    const val DISCLAIMER = "Educational pseudocode — not a decompiler"
    private const val INDENT = "    "

    private sealed interface Node
    private data class Plain(val lines: List<String>, val refs: Set<String> = emptySet()) : Node
    private data class If(val cond: Cond, val target: String) : Node
    private data class Goto(val target: String) : Node
    private data class Label(val name: String) : Node

    private data class Cond(val left: String, val op: String, val right: String) {
        fun negate(): Cond = copy(
            op = when (op) {
                "==" -> "!="
                "!=" -> "=="
                "<" -> ">="
                ">=" -> "<"
                ">" -> "<="
                else -> ">"
            },
        )

        override fun toString() = "$left $op $right"
    }

    fun generate(file: SmaliFile, method: MethodDefinition): String {
        val frame = RegisterFrame(method, file.className)
        val layout = MethodLayout(method)
        val gen = Generator(file, method, frame, layout)
        return gen.run()
    }

    private class Generator(val file: SmaliFile, val method: MethodDefinition, val frame: RegisterFrame, val layout: MethodLayout) {
        val insns = layout.instructions
        val objectRegs = HashSet<RegKey>()

        fun n(r: RegisterOperand): String = frame.preferredName(frame.key(r))

        fun run(): String {
            val nodes = ArrayList<Node>()
            val pinned = HashSet<String>()
            layout.tryRanges.forEach { pinned += it.directive.start.name; pinned += it.directive.end.name; pinned += it.directive.handler.name }

            resetObjectRegs()
            var i = 0
            while (i < insns.size) {
                layout.labelsAt[i]?.forEach { nodes += Label(it) }
                if (layout.labelsAt.containsKey(i)) objectRegs.clear()
                val (node, consumed) = translate(i)
                nodes += node
                for (k in i until i + consumed) trackObjects(insns[k])
                i += consumed
            }
            // Labels after the last instruction.
            layout.labelTargets.filter { (_, t) -> t is com.riyadm.smalihelperplus.core.smali.analysis.LabelTarget.Code && t.index == insns.size }
                .keys.forEach { nodes += Label(it) }

            val refs = HashMap<String, Int>()
            nodes.forEach { node ->
                when (node) {
                    is If -> refs.merge(node.target, 1, Int::plus)
                    is Goto -> refs.merge(node.target, 1, Int::plus)
                    is Plain -> node.refs.forEach { refs.merge(it, 1, Int::plus) }
                    is Label -> Unit
                }
            }
            pinned.forEach { refs.merge(it, 1, Int::plus) }
            structure(nodes, refs)

            val out = ArrayList<String>()
            out += "// $DISCLAIMER."
            out += "// Registers keep their smali names; parameters are named after their p-registers."
            val body = INDENT.takeIf { !method.isImplicit } ?: ""
            if (method.isImplicit) {
                out += "// Snippet without an enclosing .method: parameters and return type are unknown."
            } else {
                val outline = StructureAnalyzer.methodOutline(method, file.header?.descriptor?.let { Descriptors.simpleName(it) })
                out += outline.javaSignature + if (method.isAbstractOrNative) ";" else " {"
            }
            layout.tryRanges.forEach { r ->
                val type = r.exceptionType?.let { Descriptors.simpleName(it) } ?: "any exception"
                out += "$body// try range ${r.directive.start.text} .. ${r.directive.end.text}: $type is handled at ${r.directive.handler.text}"
            }
            nodes.forEach { node ->
                when (node) {
                    is Plain -> node.lines.forEach { out += body + it }
                    is If -> out += "${body}if (${node.cond}) goto ${node.target};"
                    is Goto -> out += "${body}goto ${node.target};"
                    is Label -> if ((refs[node.name] ?: 0) > 0) out += "$body${node.name}:"
                }
            }
            if (!method.isImplicit && !method.isAbstractOrNative) out += "}"
            return out.joinToString("\n")
        }

        // ------------------------------------------------------------------------------------------------------------
        // Structuring: if-then and if-then-else over straight-line blocks.
        // ------------------------------------------------------------------------------------------------------------

        private fun structure(nodes: MutableList<Node>, refs: MutableMap<String, Int>) {
            var changed = true
            var guard = 0
            while (changed && guard++ < 200) {
                changed = false
                for (i in nodes.indices) {
                    val node = nodes[i] as? If ?: continue
                    var j = i + 1
                    while (j < nodes.size && nodes[j] is Plain) j++
                    if (j == i + 1 || j >= nodes.size) continue
                    val then = nodes.subList(i + 1, j).flatMap { (it as Plain).lines }
                    val thenRefs = nodes.subList(i + 1, j).flatMap { (it as Plain).refs }.toSet()
                    val atJ = nodes[j]
                    // if-then: if (c) goto L; A; L:
                    if (atJ is Label && atJ.name == node.target) {
                        val block = listOf("if (${node.cond.negate()}) {") + then.map { INDENT + it } + "}"
                        refs.merge(node.target, -1, Int::plus)
                        val replacement = Plain(block, thenRefs)
                        repeat(j - i) { nodes.removeAt(i) }
                        nodes.add(i, replacement)
                        changed = true
                        break
                    }
                    // if-else: if (c) goto L; A; goto E; L: B; E:
                    if (atJ is Goto && j + 1 < nodes.size) {
                        val elseLabel = nodes[j + 1] as? Label ?: continue
                        if (elseLabel.name != node.target || (refs[node.target] ?: 0) != 1) continue
                        var m = j + 2
                        while (m < nodes.size && nodes[m] is Plain) m++
                        if (m == j + 2 || m >= nodes.size) continue
                        val endLabel = nodes[m] as? Label ?: continue
                        if (endLabel.name != atJ.target) continue
                        val elseLines = nodes.subList(j + 2, m).flatMap { (it as Plain).lines }
                        val elseRefs = nodes.subList(j + 2, m).flatMap { (it as Plain).refs }.toSet()
                        val block = listOf("if (${node.cond.negate()}) {") + then.map { INDENT + it } + "} else {" +
                            elseLines.map { INDENT + it } + "}"
                        refs.merge(node.target, -1, Int::plus)
                        refs.merge(atJ.target, -1, Int::plus)
                        repeat(m - i) { nodes.removeAt(i) }
                        nodes.add(i, Plain(block, thenRefs + elseRefs))
                        changed = true
                        break
                    }
                }
            }
        }

        // ------------------------------------------------------------------------------------------------------------
        // Object tracking (only used to print `null` instead of `0` in comparisons right after an object definition).
        // ------------------------------------------------------------------------------------------------------------

        private fun resetObjectRegs() {
            objectRegs.clear()
            val locals = frame.locals
            if (frame.isKnown && locals != null) {
                frame.parameterSlots.forEachIndexed { idx, slot ->
                    if (!slot.isHighWord && Descriptors.isReference(slot.type)) objectRegs += RegKey('v', locals + idx)
                }
            }
        }

        private fun trackObjects(insn: Instruction) {
            val op = insn.opcode ?: return
            val usage = RegisterAnalyzer.usage(insn, frame)
            val producesObject = when (op.family) {
                OpcodeFamily.CONST_STRING, OpcodeFamily.CONST_CLASS, OpcodeFamily.NEW_INSTANCE, OpcodeFamily.NEW_ARRAY,
                OpcodeFamily.MOVE_EXCEPTION, OpcodeFamily.CONST_METHOD_HANDLE, OpcodeFamily.CONST_METHOD_TYPE -> true
                OpcodeFamily.MOVE_RESULT, OpcodeFamily.MOVE, OpcodeFamily.ARRAY_GET, OpcodeFamily.INSTANCE_GET, OpcodeFamily.STATIC_GET ->
                    op.mnemonic.contains("object")
                else -> false
            }
            usage.writes.forEach { if (producesObject) objectRegs += it else objectRegs -= it }
        }

        // ------------------------------------------------------------------------------------------------------------
        // Translation
        // ------------------------------------------------------------------------------------------------------------

        private fun untranslated(insn: Instruction) = Plain(listOf("/* untranslated: ${insn.text} */"))

        private fun next(i: Int): Instruction? = insns.getOrNull(i + 1)?.takeIf { !layout.labelsAt.containsKey(i + 1) }

        private fun translate(i: Int): Pair<Node, Int> {
            val insn = insns[i]
            val op = insn.opcode ?: return untranslated(insn) to 1
            val o = insn.operands
            return try {
                translateKnown(i, insn, op.family, o)
            } catch (_: RuntimeException) {
                untranslated(insn) to 1 // operands do not match the format
            }
        }

        private fun r(o: List<Operand>, i: Int) = n(o[i] as RegisterOperand)

        private fun translateKnown(i: Int, insn: Instruction, family: OpcodeFamily, o: List<Operand>): Pair<Node, Int> {
            val op = insn.opcode!!
            fun stmt(s: String) = Plain(listOf(s)) to 1
            return when (family) {
                OpcodeFamily.NOP -> stmt("// nop")
                OpcodeFamily.MOVE -> stmt("${r(o, 0)} = ${r(o, 1)};")
                OpcodeFamily.MOVE_RESULT -> untranslated(insn) to 1 // only valid when merged with the preceding call
                OpcodeFamily.MOVE_EXCEPTION -> {
                    val types = layout.tryRanges.filter { it.handlerIndex == i }
                        .map { it.exceptionType?.let { t -> Descriptors.simpleName(t) } ?: "Throwable" }.distinct()
                    stmt("${r(o, 0)} = caughtException;" + if (types.isNotEmpty()) "  // ${types.joinToString(" | ")}" else "")
                }
                OpcodeFamily.RETURN -> stmt(if (o.isEmpty()) "return;" else "return ${r(o, 0)};")
                OpcodeFamily.CONST -> stmt("${r(o, 0)} = ${constValue(insn)};")
                OpcodeFamily.CONST_STRING -> stmt("${r(o, 0)} = ${(o[1] as StringOperand).text};")
                OpcodeFamily.CONST_CLASS -> stmt("${r(o, 0)} = ${Descriptors.toJavaName(o[1].text, true)}.class;")
                OpcodeFamily.CHECK_CAST -> stmt("${r(o, 0)} = (${Descriptors.toJavaName(o[1].text, true)}) ${r(o, 0)};")
                OpcodeFamily.INSTANCE_OF -> stmt("${r(o, 0)} = ${r(o, 1)} instanceof ${Descriptors.toJavaName(o[2].text, true)};")
                OpcodeFamily.ARRAY_LENGTH -> stmt("${r(o, 0)} = ${r(o, 1)}.length;")
                OpcodeFamily.NEW_INSTANCE -> newInstance(i, insn, o)
                OpcodeFamily.NEW_ARRAY -> {
                    val type = Descriptors.toJavaName(o[2].text, true)
                    val base = type.removeSuffix("[]")
                    stmt("${r(o, 0)} = new $base[${r(o, 1)}];")
                }
                OpcodeFamily.FILLED_NEW_ARRAY -> {
                    val values = args(o[0]).joinToString(", ")
                    val expr = "new ${Descriptors.toJavaName(o[1].text, true)} {$values}"
                    val nx = next(i)
                    if (nx?.opcode?.mnemonic == "move-result-object") {
                        Plain(listOf("${n(nx.operands[0] as RegisterOperand)} = $expr;")) to 2
                    } else {
                        stmt("$expr;  // result discarded")
                    }
                }
                OpcodeFamily.FILL_ARRAY_DATA -> {
                    val label = (o[1] as LabelOperand)
                    val payload = layout.payload(label.name) ?: return untranslated(insn) to 1
                    stmt("fill(${r(o, 0)}, {${payload.values.joinToString(", ")}});  // fill-array-data ${label.text}")
                }
                OpcodeFamily.THROW -> stmt("throw ${r(o, 0)};")
                OpcodeFamily.GOTO -> Goto((o[0] as LabelOperand).name) to 1
                OpcodeFamily.SWITCH -> {
                    val cases = ControlFlowAnalyzer.switchCases(insn, layout, null)
                    val payload = layout.payload((o[1] as LabelOperand).name) ?: return untranslated(insn) to 1
                    val lines = ArrayList<String>()
                    lines += "switch (${r(o, 0)}) {"
                    val refs = HashSet<String>()
                    payload.targets.forEachIndexed { idx, t ->
                        val key = cases.getOrNull(idx)?.first ?: (payload.firstKey?.plus(idx) ?: payload.keys.getOrNull(idx))
                        lines += "${INDENT}case $key: goto ${t.name};"
                        refs += t.name
                    }
                    lines += "}  // no match: continue below"
                    Plain(lines, refs) to 1
                }
                OpcodeFamily.COMPARE -> {
                    val a = r(o, 1)
                    val b = r(o, 2)
                    when {
                        op.mnemonic == "cmp-long" -> stmt("${r(o, 0)} = Long.compare($a, $b);")
                        op.mnemonic.startsWith("cmpl") -> stmt("${r(o, 0)} = ($a > $b) ? 1 : ($a == $b) ? 0 : -1;  // NaN gives -1")
                        else -> stmt("${r(o, 0)} = ($a < $b) ? -1 : ($a == $b) ? 0 : 1;  // NaN gives 1")
                    }
                }
                OpcodeFamily.IF_TEST -> If(Cond(r(o, 0), relation(op.mnemonic.removePrefix("if-")), r(o, 1)), (o[2] as LabelOperand).name) to 1
                OpcodeFamily.IF_TESTZ -> {
                    val reg = o[0] as RegisterOperand
                    val test = op.mnemonic.removePrefix("if-").removeSuffix("z")
                    val zero = if ((test == "eq" || test == "ne") && frame.key(reg) in objectRegs) "null" else "0"
                    If(Cond(n(reg), relation(test), zero), (o[1] as LabelOperand).name) to 1
                }
                OpcodeFamily.ARRAY_GET -> stmt("${r(o, 0)} = ${r(o, 1)}[${r(o, 2)}];")
                OpcodeFamily.ARRAY_PUT -> stmt("${r(o, 1)}[${r(o, 2)}] = ${r(o, 0)};")
                OpcodeFamily.INSTANCE_GET -> {
                    val f = Descriptors.parseFieldReference(o[2].text) ?: return untranslated(insn) to 1
                    stmt("${r(o, 0)} = ${r(o, 1)}.${f.name};")
                }
                OpcodeFamily.INSTANCE_PUT -> {
                    val f = Descriptors.parseFieldReference(o[2].text) ?: return untranslated(insn) to 1
                    stmt("${r(o, 1)}.${f.name} = ${r(o, 0)};")
                }
                OpcodeFamily.STATIC_GET -> {
                    val f = Descriptors.parseFieldReference(o[1].text) ?: return untranslated(insn) to 1
                    stmt("${r(o, 0)} = ${Descriptors.simpleName(f.owner)}.${f.name};")
                }
                OpcodeFamily.STATIC_PUT -> {
                    val f = Descriptors.parseFieldReference(o[1].text) ?: return untranslated(insn) to 1
                    stmt("${Descriptors.simpleName(f.owner)}.${f.name} = ${r(o, 0)};")
                }
                OpcodeFamily.INVOKE -> invoke(i, insn, o)
                OpcodeFamily.UNARY -> stmt("${r(o, 0)} = ${if (op.mnemonic.startsWith("neg")) "-" else "~"}${r(o, 1)};")
                OpcodeFamily.CONVERSION -> stmt("${r(o, 0)} = (${op.mnemonic.substringAfter("-to-")}) ${r(o, 1)};")
                OpcodeFamily.BINARY -> stmt("${r(o, 0)} = ${r(o, 1)} ${symbol(op.mnemonic)} ${r(o, 2)};")
                OpcodeFamily.BINARY_2ADDR -> stmt("${r(o, 0)} ${symbol(op.mnemonic)}= ${r(o, 1)};")
                OpcodeFamily.BINARY_LIT16, OpcodeFamily.BINARY_LIT8 -> {
                    val lit = InstructionExplainer.literalValue(o[2] as LiteralOperand, op.operands[2] as OperandSpec.Literal)
                        ?: return untranslated(insn) to 1
                    if (op.mnemonic.startsWith("rsub-int")) stmt("${r(o, 0)} = $lit - ${r(o, 1)};")
                    else stmt("${r(o, 0)} = ${r(o, 1)} ${symbol(op.mnemonic)} $lit;")
                }
                OpcodeFamily.MONITOR, OpcodeFamily.INVOKE_POLYMORPHIC, OpcodeFamily.INVOKE_CUSTOM,
                OpcodeFamily.CONST_METHOD_HANDLE, OpcodeFamily.CONST_METHOD_TYPE -> untranslated(insn) to 1
            }
        }

        private fun relation(test: String) = when (test) {
            "eq" -> "=="
            "ne" -> "!="
            "lt" -> "<"
            "ge" -> ">="
            "gt" -> ">"
            else -> "<="
        }

        private fun symbol(mnemonic: String): String = when (mnemonic.substringBefore('-')) {
            "add" -> "+"
            "sub" -> "-"
            "mul" -> "*"
            "div" -> "/"
            "rem" -> "%"
            "and" -> "&"
            "or" -> "|"
            "xor" -> "^"
            "shl" -> "<<"
            "shr" -> ">>"
            "ushr" -> ">>>"
            else -> "?"
        }

        private fun constValue(insn: Instruction): String {
            val op = insn.opcode!!
            val lit = insn.operands[1] as LiteralOperand
            val spec = op.operands[1] as OperandSpec.Literal
            if (lit.isFloatingPoint) {
                if (spec.wide && lit.type == LiteralType.FLOAT) {
                    return java.lang.Float.floatToRawIntBits(lit.floatValue!!.toFloat()).toLong().toString() + "L /* raw float bits */"
                }
                val core = lit.text.trimEnd('d', 'D')
                return if (lit.type == LiteralType.FLOAT) core.trimEnd('f', 'F') + "f" else core
            }
            val value = InstructionExplainer.literalValue(lit, spec) ?: return lit.text
            return when (op.mnemonic) {
                "const/high16" -> "0x%08x /* %sf if read as a float */".format(value.toInt(), java.lang.Float.intBitsToFloat(value.toInt()))
                "const-wide/high16" -> "0x%016xL /* %s if read as a double */".format(value.toLong(), java.lang.Double.longBitsToDouble(value.toLong()))
                else -> {
                    val wide = spec.wide
                    val text = if (lit.radixPrefixed && value >= BigInteger.valueOf(256)) "0x" + value.toString(16) else value.toString()
                    if (wide) text + "L" else text
                }
            }
        }

        private fun args(o: Operand): List<String> = when (o) {
            is RegisterListOperand -> o.registers.map { n(it) }
            is RegisterRangeOperand -> {
                val a = frame.key(o.first)
                val b = frame.key(o.last)
                if (a.prefix == b.prefix && b.number >= a.number && b.number - a.number < 256) {
                    (a.number..b.number).map { frame.preferredName(RegKey(a.prefix, it)) }
                } else {
                    throw IllegalArgumentException("bad range")
                }
            }
            else -> throw IllegalArgumentException("not a register list")
        }

        /** Arguments mapped to parameters (wide parameters use the first register of the pair). */
        private fun callArgs(regs: List<String>, params: List<String>, skip: Int): List<String> {
            val out = ArrayList<String>()
            var pos = skip
            params.forEach { t ->
                out += regs.getOrElse(pos) { "?" }
                pos += Descriptors.wordSize(t)
            }
            return out
        }

        private fun invoke(i: Int, insn: Instruction, o: List<Operand>): Pair<Node, Int> {
            val op = insn.opcode!!
            val ref = Descriptors.parseMethodReference(o[1].text) ?: return untranslated(insn) to 1
            val regs = args(o[0])
            val kind = op.mnemonic.removePrefix("invoke-").removeSuffix("/range")
            val isStatic = kind == "static"
            val callArgs = callArgs(regs, ref.proto.parameters, if (isStatic) 0 else 1).joinToString(", ")
            val receiver = regs.firstOrNull() ?: "?"
            val expr = when {
                ref.name == "<init>" -> {
                    if (receiver == "this" && method.name == "<init>") {
                        if (ref.owner == file.className) "this($callArgs)" else "super($callArgs)"
                    } else {
                        "$receiver.<init>($callArgs)  /* constructor call */"
                    }
                }
                isStatic -> "${Descriptors.simpleName(ref.owner)}.${ref.name}($callArgs)"
                kind == "super" -> if (receiver == "this") "super.${ref.name}($callArgs)" else "$receiver.${ref.name}($callArgs) /* super */"
                else -> "$receiver.${ref.name}($callArgs)"
            }
            val nx = next(i)
            if (ref.proto.returnType != "V" && nx?.opcode?.family == OpcodeFamily.MOVE_RESULT) {
                return Plain(listOf("${n(nx.operands[0] as RegisterOperand)} = $expr;")) to 2
            }
            return Plain(listOf("$expr;")) to 1
        }

        private fun newInstance(i: Int, insn: Instruction, o: List<Operand>): Pair<Node, Int> {
            val dest = o[0] as RegisterOperand
            val type = Descriptors.toJavaName(o[1].text, true)
            val nx = next(i)
            val nxOp = nx?.opcode
            if (nx != null && nxOp != null && nxOp.family == OpcodeFamily.INVOKE && nxOp.mnemonic.startsWith("invoke-direct")) {
                val ref = Descriptors.parseMethodReference(nx.operands.getOrNull(1)?.text ?: "")
                val regs = runCatching { args(nx.operands[0]) }.getOrNull()
                if (ref != null && ref.name == "<init>" && regs != null && regs.firstOrNull() == n(dest)) {
                    val callArgs = callArgs(regs, ref.proto.parameters, 1).joinToString(", ")
                    return Plain(listOf("${n(dest)} = new $type($callArgs);")) to 2
                }
            }
            return Plain(listOf("${n(dest)} = new $type;  // allocated; the constructor is called separately")) to 1
        }
    }
}
