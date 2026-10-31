package com.riyadm.smalihelperplus.core.smali.explain

import com.riyadm.smalihelperplus.core.smali.ast.Instruction
import com.riyadm.smalihelperplus.core.smali.ast.LabelOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralType
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.Operand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterListOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterRangeOperand
import com.riyadm.smalihelperplus.core.smali.ast.StringOperand
import com.riyadm.smalihelperplus.core.smali.analysis.MethodLayout
import com.riyadm.smalihelperplus.core.smali.model.Descriptors
import com.riyadm.smalihelperplus.core.smali.opcodes.DalvikOpcode
import com.riyadm.smalihelperplus.core.smali.opcodes.LiteralKind
import com.riyadm.smalihelperplus.core.smali.opcodes.OpcodeFamily
import com.riyadm.smalihelperplus.core.smali.opcodes.OperandSpec
import com.riyadm.smalihelperplus.core.smali.opcodes.PayloadKind
import java.math.BigInteger

/** Plain-English explanation of one instruction. */
data class InstructionExplanation(
    val index: Int,
    val line: Int,
    val code: String,
    val explanation: String,
    /** Extra notes, e.g. exceptions that may be thrown. */
    val notes: List<String> = emptyList(),
)

/**
 * Produces original, plain-English explanations of instructions using the actual registers and values. Explanations
 * describe the documented Dalvik semantics only; nothing is executed.
 */
object InstructionExplainer {

    fun explain(insn: Instruction, method: MethodDefinition? = null, layout: MethodLayout? = null): InstructionExplanation {
        val op = insn.opcode
            ?: return InstructionExplanation(insn.index, insn.line, insn.text, "`${insn.mnemonic}` is not a Dalvik instruction, so it cannot be explained.")
        val ctx = Ctx(insn, op, method, layout)
        val text = runCatching { ctx.describe() }.getOrNull()
            ?: "This `${op.mnemonic}` instruction has operands that do not match its format (${op.format.syntax}), so it cannot be explained."
        return InstructionExplanation(insn.index, insn.line, insn.text, text, ctx.notes)
    }

    fun explainAll(method: MethodDefinition): List<InstructionExplanation> {
        val layout = MethodLayout(method)
        return method.instructions.map { explain(it, method, layout) }
    }

    /** Effective numeric value of a literal for the given operand spec (sign-extension for hex bit patterns). */
    fun literalValue(lit: LiteralOperand, spec: OperandSpec.Literal?): BigInteger? {
        val raw = lit.value ?: return null
        val v = if (spec?.kind == LiteralKind.SIGNED && lit.type == LiteralType.CHAR) {
            BigInteger.valueOf(raw.toShort().toLong())
        } else raw
        if (spec == null) return v
        if (spec.kind == LiteralKind.HIGH16 || spec.kind == LiteralKind.WIDE_HIGH16) return v
        // Hex/octal literals may spell a two's-complement bit pattern (e.g. 0xffff for a 16-bit -1).
        val maxSigned = BigInteger.ONE.shiftLeft(spec.bits - 1).subtract(BigInteger.ONE)
        return if (v > maxSigned) v.subtract(BigInteger.ONE.shiftLeft(spec.bits)) else v
    }

    private class Ctx(val insn: Instruction, val op: DalvikOpcode, val method: MethodDefinition?, val layout: MethodLayout?) {
        val notes = ArrayList<String>()
        val ops: List<Operand> = insn.operands
        val isInstanceMethod = method != null && !method.isStatic && !method.isImplicit

        fun reg(i: Int): String = name(ops[i] as RegisterOperand)

        fun name(r: RegisterOperand): String = if (r.text == "p0" && isInstanceMethod) "p0 (this)" else r.text

        /** `v0` or `the pair v0/v1` for wide operands. */
        fun regOrPair(i: Int, wide: Boolean): String {
            val r = ops[i] as RegisterOperand
            return if (wide) "the register pair ${r.text}/${r.prefix}${r.number + 1}" else "register ${name(r)}"
        }

        fun specWide(i: Int) = (op.operands.getOrNull(i) as? OperandSpec.Register)?.wide == true

        fun label(i: Int): String = (ops[i] as LabelOperand).text

        fun type(i: Int): String = Descriptors.toJavaName(ops[i].text, simple = false)

        fun describe(): String = when (op.family) {
            OpcodeFamily.NOP -> "Does nothing (no operation). The same opcode value is also used to tag data payloads."
            OpcodeFamily.MOVE -> when {
                op.mnemonic.startsWith("move-wide") -> "Copies the 64-bit value in ${regOrPair(1, true)} into ${regOrPair(0, true)}."
                op.mnemonic.startsWith("move-object") -> "Copies the object reference in ${reg(1)} into register ${reg(0)}."
                else -> "Copies the 32-bit value in ${reg(1)} into register ${reg(0)}."
            }
            OpcodeFamily.MOVE_RESULT -> moveResult()
            OpcodeFamily.MOVE_EXCEPTION -> {
                val types = handlerTypes()
                "Stores the exception that was just caught${types?.let { " ($it)" } ?: ""} in register ${reg(0)}. It must be the first instruction of a catch handler."
            }
            OpcodeFamily.RETURN -> when (op.mnemonic) {
                "return-void" -> "Returns from this method without a value."
                "return-wide" -> "Returns the 64-bit (long or double) value in ${regOrPair(0, true)} to the caller."
                "return-object" -> "Returns the object reference in ${reg(0)} to the caller."
                else -> "Returns the 32-bit (int, float, boolean, byte, char or short) value in ${reg(0)} to the caller."
            }
            OpcodeFamily.CONST -> constant()
            OpcodeFamily.CONST_STRING -> "Loads a reference to the string ${(ops[1] as StringOperand).text} into register ${reg(0)}."
            OpcodeFamily.CONST_CLASS -> "Loads the Class object for ${type(1)} into register ${reg(0)} (like `${Descriptors.toJavaName(ops[1].text, true)}.class` in Java)."
            OpcodeFamily.MONITOR -> if (op.mnemonic == "monitor-enter") {
                "Acquires the lock (monitor) of the object in ${reg(0)}, like entering a synchronized block. Waits if another thread holds it."
            } else {
                "Releases the lock (monitor) of the object in ${reg(0)}, like leaving a synchronized block."
            }
            OpcodeFamily.CHECK_CAST -> {
                notes += "Throws ClassCastException if the cast is not possible."
                "Checks that the object in ${reg(0)} can be cast to ${type(1)}. A null reference always passes."
            }
            OpcodeFamily.INSTANCE_OF -> "Sets ${reg(0)} to 1 if the object in ${reg(1)} is an instance of ${type(2)}, otherwise to 0 (null gives 0)."
            OpcodeFamily.ARRAY_LENGTH -> "Stores the length of the array in ${reg(1)} into register ${reg(0)}."
            OpcodeFamily.NEW_INSTANCE -> "Allocates a new ${type(1)} object and stores the reference in ${reg(0)}. The object is not usable until one of its constructors (<init>) is invoked on it."
            OpcodeFamily.NEW_ARRAY -> "Creates a new ${type(2).let { arrayWithLength(it, reg(1)) }} and stores it in ${reg(0)}."
            OpcodeFamily.FILLED_NEW_ARRAY -> {
                val regs = argumentRegisters(ops[0])
                "Creates a new ${type(1)} array holding the values of ${regs.ifEmpty { listOf("no registers") }.joinToString(", ")}. The array becomes the result of this instruction, so a following move-result-object is needed to keep it."
            }
            OpcodeFamily.FILL_ARRAY_DATA -> {
                val payload = layout?.payload(label(1))
                val detail = payload?.let { p -> " (${p.values.size} element${if (p.values.size == 1) "" else "s"} of ${p.elementWidth} byte${if (p.elementWidth == 1) "" else "s"} each)" } ?: ""
                "Copies the constant data from the payload at ${label(1)}$detail into the array in ${reg(0)}."
            }
            OpcodeFamily.THROW -> "Throws the exception object held in ${reg(0)}."
            OpcodeFamily.GOTO -> "Jumps unconditionally to ${label(0)}."
            OpcodeFamily.SWITCH -> switch()
            OpcodeFamily.COMPARE -> compare()
            OpcodeFamily.IF_TEST -> "Jumps to ${label(2)} if ${reg(0)} ${relation(op.mnemonic.removePrefix("if-"))} ${reg(1)}; otherwise continues with the next instruction."
            OpcodeFamily.IF_TESTZ -> "Jumps to ${label(1)} if ${reg(0)} ${zeroRelation(op.mnemonic.removePrefix("if-").removeSuffix("z"))}; otherwise continues with the next instruction."
            OpcodeFamily.ARRAY_GET -> {
                notes += "Throws NullPointerException for a null array and ArrayIndexOutOfBoundsException for a bad index."
                "Reads the ${elementKind(op.mnemonic)} element at index ${reg(2)} of the array in ${reg(1)} into ${regOrPair(0, specWide(0))}."
            }
            OpcodeFamily.ARRAY_PUT -> {
                notes += "Throws NullPointerException for a null array and ArrayIndexOutOfBoundsException for a bad index."
                "Stores the ${elementKind(op.mnemonic)} value in ${regOrPair(0, specWide(0))} into the array in ${reg(1)} at index ${reg(2)}."
            }
            OpcodeFamily.INSTANCE_GET -> "Reads the instance field ${field(2)} of the object in ${reg(1)} into ${regOrPair(0, specWide(0))}."
            OpcodeFamily.INSTANCE_PUT -> "Writes the value in ${regOrPair(0, specWide(0))} into the instance field ${field(2)} of the object in ${reg(1)}."
            OpcodeFamily.STATIC_GET -> "Reads the static field ${field(1)} into ${regOrPair(0, specWide(0))}."
            OpcodeFamily.STATIC_PUT -> "Writes the value in ${regOrPair(0, specWide(0))} into the static field ${field(1)}."
            OpcodeFamily.INVOKE -> invoke()
            OpcodeFamily.UNARY -> {
                val wide = specWide(0)
                val type = op.mnemonic.substringAfter('-')
                if (op.mnemonic.startsWith("neg")) "Negates the $type in ${regOrPair(1, wide)} and stores the result in ${regOrPair(0, wide)}."
                else "Stores the bitwise complement (~) of the $type in ${regOrPair(1, wide)} into ${regOrPair(0, wide)}."
            }
            OpcodeFamily.CONVERSION -> conversion()
            OpcodeFamily.BINARY -> {
                val (sym, type) = binary(op.mnemonic)
                divNote(op.mnemonic)
                "Computes ${regOrPair(1, specWide(1)).withoutArticle()} $sym ${regOrPair(2, specWide(2)).withoutArticle()} as $type and stores the result in ${regOrPair(0, specWide(0))}."
            }
            OpcodeFamily.BINARY_2ADDR -> {
                val (sym, type) = binary(op.mnemonic.removeSuffix("/2addr"))
                divNote(op.mnemonic)
                "Computes ${regOrPair(0, specWide(0)).withoutArticle()} $sym ${regOrPair(1, specWide(1)).withoutArticle()} as $type and stores the result back in ${regOrPair(0, specWide(0))} (the \"2addr\" form reuses the first operand as destination)."
            }
            OpcodeFamily.BINARY_LIT16, OpcodeFamily.BINARY_LIT8 -> {
                val lit = literalText(2)
                divNote(op.mnemonic)
                if (op.mnemonic.startsWith("rsub-int")) {
                    "Computes $lit - ${reg(1)} (reverse subtraction) and stores the int result in ${reg(0)}."
                } else {
                    val (sym, _) = binary(op.mnemonic.substringBefore('/'))
                    "Computes ${reg(1)} $sym $lit as int and stores the result in ${reg(0)}."
                }
            }
            OpcodeFamily.INVOKE_POLYMORPHIC -> {
                val regs = argumentRegisters(ops[0])
                "Calls the signature-polymorphic method ${methodName(ops[1].text)} on the method handle in ${regs.firstOrNull() ?: "?"} with call-site prototype ${ops[2].text}${if (regs.size > 1) ", passing ${regs.drop(1).joinToString(", ")}" else ""}. Requires API 26+."
            }
            OpcodeFamily.INVOKE_CUSTOM -> {
                val regs = argumentRegisters(ops[0])
                "Resolves the call site ${ops[1].text.substringBefore('(')} through its bootstrap method and invokes its target${if (regs.isNotEmpty()) " with ${regs.joinToString(", ")}" else ""}. Requires API 26+."
            }
            OpcodeFamily.CONST_METHOD_HANDLE -> "Loads a reference to the method handle ${ops[1].text} into register ${reg(0)}. Requires API 28+."
            OpcodeFamily.CONST_METHOD_TYPE -> "Loads a reference to the method type ${ops[1].text} into register ${reg(0)}. Requires API 28+."
        }

        private fun String.withoutArticle(): String = removePrefix("register ")

        private fun divNote(mnemonic: String) {
            if ((mnemonic.startsWith("div-int") || mnemonic.startsWith("rem-int") || mnemonic.startsWith("div-long") || mnemonic.startsWith("rem-long"))) {
                notes += "Integer division by zero throws ArithmeticException."
            }
        }

        private fun moveResult(): String {
            val prev = layout?.instructions?.getOrNull(insn.index - 1)
            val source = when (prev?.opcode?.family) {
                OpcodeFamily.INVOKE -> prev.operands.getOrNull(1)?.let { " of the call to ${methodName(it.text)}" }
                OpcodeFamily.FILLED_NEW_ARRAY -> " (the array created by filled-new-array)"
                else -> null
            } ?: " of the immediately preceding invoke"
            return when (op.mnemonic) {
                "move-result-wide" -> "Stores the 64-bit result$source in ${regOrPair(0, true)}."
                "move-result-object" -> "Stores the object result$source in register ${reg(0)}."
                else -> "Stores the 32-bit result$source in register ${reg(0)}."
            }
        }

        private fun handlerTypes(): String? {
            val l = layout ?: return null
            val types = l.tryRanges.filter { it.handlerIndex == insn.index }.map { it.exceptionType?.let { t -> Descriptors.toJavaName(t, true) } ?: "any exception" }.distinct()
            return types.takeIf { it.isNotEmpty() }?.joinToString(" or ")
        }

        private fun literalText(i: Int): String {
            val lit = ops[i] as LiteralOperand
            val spec = op.operands.getOrNull(i) as? OperandSpec.Literal
            if (lit.isFloatingPoint) return lit.text
            return literalValue(lit, spec)?.toString() ?: lit.text
        }

        private fun constant(): String {
            val lit = ops[1] as LiteralOperand
            val spec = op.operands[1] as OperandSpec.Literal
            val wide = spec.wide
            val dest = if (wide) regOrPair(0, true) else "register ${reg(0)}"
            if (lit.isFloatingPoint) {
                if (wide && lit.type == LiteralType.FLOAT) {
                    val bits = java.lang.Float.floatToRawIntBits(lit.floatValue!!.toFloat()).toLong()
                    return "Loads the sign-extended raw float bits $bits into $dest; this is not a double conversion."
                }
                val kind = if (lit.type == LiteralType.FLOAT) "float" else "double"
                return "Loads the $kind value ${lit.text.trimEnd('f', 'F', 'd', 'D')} into $dest."
            }
            val value = literalValue(lit, spec) ?: return "Loads ${lit.text} into $dest."
            val reinterpreted = value.signum() < 0 && !lit.text.startsWith("-")
            val hexNote = if (lit.radixPrefixed && (value.abs() > BigInteger.valueOf(9) || reinterpreted)) " (${lit.text})" else ""
            return when (spec.kind) {
                LiteralKind.HIGH16 -> {
                    val bits = value.toInt()
                    "Loads the 32-bit value 0x%08x into register %s. const/high16 encodes only the upper 16 bits; the lower 16 bits are zero. Read as an int this is %d; read as a float it is %s.".format(
                        bits, reg(0), bits, java.lang.Float.intBitsToFloat(bits).toString(),
                    )
                }
                LiteralKind.WIDE_HIGH16 -> {
                    val bits = value.toLong()
                    "Loads the 64-bit value 0x%016x into %s. const-wide/high16 encodes only the upper 16 bits; the lower 48 bits are zero. Read as a double this is %s.".format(
                        bits, regOrPair(0, true), java.lang.Double.longBitsToDouble(bits).toString(),
                    )
                }
                else -> if (wide) {
                    "Loads the long value $value$hexNote into $dest."
                } else {
                    "Loads the integer value $value$hexNote into $dest."
                }
            }
        }

        private fun switch(): String {
            val payload = layout?.payload(label(1))
            val detail = when {
                payload == null -> ""
                payload.kind == PayloadKind.PACKED_SWITCH -> {
                    val first = payload.firstKey ?: 0
                    " It has ${payload.targets.size} consecutive case${if (payload.targets.size == 1) "" else "s"} starting at $first."
                }
                else -> " It has ${payload.targets.size} case${if (payload.targets.size == 1) "" else "s"} (${payload.keys.joinToString(", ")})."
            }
            val kind = if (op.mnemonic == "packed-switch") "packed (consecutive keys)" else "sparse (sorted key/target pairs)"
            return "Jumps to the case target selected by the value in ${reg(0)}, using the $kind switch table at ${label(1)}.$detail If no case matches, execution continues with the next instruction."
        }

        private fun compare(): String {
            val wide = specWide(1)
            val a = regOrPair(1, wide).withoutArticle()
            val b = regOrPair(2, wide).withoutArticle()
            val type = when (op.mnemonic) {
                "cmp-long" -> "long"
                "cmpl-double", "cmpg-double" -> "double"
                else -> "float"
            }
            val nan = when {
                op.mnemonic.startsWith("cmpl") -> " If either value is NaN the result is -1 (\"lt bias\")."
                op.mnemonic.startsWith("cmpg") -> " If either value is NaN the result is 1 (\"gt bias\")."
                else -> ""
            }
            return "Compares the $type values $a and $b and stores 0 in ${reg(0)} if they are equal, 1 if the first is greater, or -1 if it is smaller.$nan"
        }

        private fun relation(test: String) = when (test) {
            "eq" -> "equals"
            "ne" -> "is not equal to"
            "lt" -> "is less than"
            "ge" -> "is greater than or equal to"
            "gt" -> "is greater than"
            else -> "is less than or equal to"
        }

        private fun zeroRelation(test: String) = when (test) {
            "eq" -> "is zero (or null for an object reference)"
            "ne" -> "is not zero (or not null for an object reference)"
            "lt" -> "is less than zero"
            "ge" -> "is greater than or equal to zero"
            "gt" -> "is greater than zero"
            else -> "is less than or equal to zero"
        }

        private fun elementKind(mnemonic: String): String = when (mnemonic.substringAfter('-', "")) {
            "wide" -> "64-bit (long or double)"
            "object" -> "object reference"
            "boolean" -> "boolean"
            "byte" -> "byte"
            "char" -> "char"
            "short" -> "short"
            else -> "32-bit (int or float)"
        }

        private fun field(i: Int): String {
            val ref = Descriptors.parseFieldReference(ops[i].text) ?: return ops[i].text
            return "${Descriptors.simpleName(ref.owner)}.${ref.name} (${Descriptors.toJavaName(ref.type, true)})"
        }

        fun methodName(text: String): String {
            val ref = Descriptors.parseMethodReference(text) ?: return text
            val params = ref.proto.parameters.joinToString(", ") { Descriptors.toJavaName(it, true) }
            return "${Descriptors.simpleName(ref.owner)}.${ref.name}($params)"
        }

        fun argumentRegisters(o: Operand): List<String> = when (o) {
            is RegisterListOperand -> o.registers.map { name(it) }
            is RegisterRangeOperand -> {
                if (o.first.prefix == o.last.prefix && o.last.number >= o.first.number && o.last.number - o.first.number < 256) {
                    (o.first.number..o.last.number).map { n -> "${o.first.prefix}$n" }.map { if (it == "p0" && isInstanceMethod) "p0 (this)" else it }
                } else {
                    listOf(o.first.text, o.last.text)
                }
            }
            else -> emptyList()
        }

        private fun invoke(): String {
            val ref = Descriptors.parseMethodReference(ops[1].text)
                ?: return "Calls the method ${ops[1].text}."
            val regs = argumentRegisters(ops[0])
            val kind = op.mnemonic.removePrefix("invoke-").removeSuffix("/range")
            val isStatic = kind == "static"
            val receiver = if (isStatic) null else regs.firstOrNull()
            // Pair up argument registers with parameter types (wide parameters take two registers).
            val args = ArrayList<String>()
            var pos = if (isStatic) 0 else 1
            ref.proto.parameters.forEach { t ->
                val r = regs.getOrNull(pos)
                if (r != null) {
                    args += if (Descriptors.isWide(t)) "$r/${regs.getOrNull(pos + 1) ?: "?"} (${Descriptors.toJavaName(t, true)})" else "$r (${Descriptors.toJavaName(t, true)})"
                }
                pos += Descriptors.wordSize(t)
            }
            val argText = if (args.isEmpty()) "with no arguments" else "with argument${if (args.size == 1) "" else "s"} ${args.joinToString(", ")}"
            val owner = Descriptors.toJavaName(ref.owner, true)
            val head = when {
                ref.name == "<init>" -> "Runs the constructor of $owner on the object in ${receiver ?: "?"} $argText."
                kind == "static" -> "Calls the static method $owner.${ref.name}() $argText."
                kind == "super" -> "Calls the superclass implementation $owner.${ref.name}() on ${receiver ?: "?"} $argText (bypassing overrides in this class)."
                kind == "direct" -> "Calls the private method $owner.${ref.name}() on ${receiver ?: "?"} $argText (no virtual dispatch)."
                kind == "interface" -> "Calls the interface method $owner.${ref.name}() on the object in ${receiver ?: "?"} $argText."
                else -> "Calls the virtual method $owner.${ref.name}() on the object in ${receiver ?: "?"} $argText; the implementation is chosen from the object's runtime class."
            }
            val ret = ref.proto.returnType
            val tail = if (ret == "V") "" else " It returns ${Descriptors.toJavaName(ret, true)}; a following ${resultOp(ret)} can store the result."
            return head + tail
        }

        private fun resultOp(ret: String) = when {
            Descriptors.isWide(ret) -> "move-result-wide"
            Descriptors.isReference(ret) -> "move-result-object"
            else -> "move-result"
        }

        private fun conversion(): String {
            val from = op.mnemonic.substringBefore("-to-")
            val to = op.mnemonic.substringAfter("-to-")
            val src = regOrPair(1, specWide(1))
            val dst = regOrPair(0, specWide(0))
            val note = when {
                to == "byte" || to == "short" -> " The value is truncated to ${if (to == "byte") 8 else 16} bits and sign-extended."
                to == "char" -> " The value is truncated to 16 bits and zero-extended."
                (from == "float" || from == "double") && (to == "int" || to == "long") -> " The fraction is discarded (rounds toward zero)."
                else -> ""
            }
            return "Converts the $from in $src to $to and stores it in $dst.$note"
        }

        private fun arrayWithLength(arrayType: String, lengthReg: String): String {
            val element = arrayType.removeSuffix("[]")
            return "$arrayType array (element type $element) whose length is the value in $lengthReg"
        }

        private fun binary(mnemonic: String): Pair<String, String> {
            val opName = mnemonic.substringBefore('-')
            val type = mnemonic.substringAfter('-', "int").substringBefore('/')
            val sym = when (opName) {
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
                else -> opName
            }
            return sym to type
        }
    }
}
