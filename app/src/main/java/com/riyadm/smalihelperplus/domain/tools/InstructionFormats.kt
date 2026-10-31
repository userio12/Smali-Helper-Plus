package com.riyadm.smalihelperplus.domain.tools

/** One field (box) of an instruction layout diagram: a label and its width in bits. */
data class LayoutCell(val label: String, val bits: Int) {
    val isOpcode: Boolean get() = label == "op"
    val isZero: Boolean get() = label.all { it == 'Ø' }
}

/**
 * A Dalvik instruction format as listed on the official "Dalvik executable instruction formats" page.
 * Text fields are original explanations; [layout] and [syntax] follow the official notation.
 */
data class InstructionFormat(
    val id: String,
    /** Bitwise layout, one space-separated word per 16-bit code unit, e.g. "B|A|op CCCC". */
    val layout: String,
    /** Syntax variants, e.g. "op vA, vB". */
    val syntax: List<String>,
    /** Number of 16-bit code units. */
    val codeUnits: Int,
    /** What the id letters/digits mean in this specific case. */
    val idMeaning: String,
    /** Plain-language description of the format. */
    val description: String,
    /** Smali examples (empty for formats that never appear in regular DEX files). */
    val examples: List<String>,
    /** True for formats that are only suggested for optimizers / verifiers and are not valid in regular DEX files. */
    val optimizerOnly: Boolean,
    /** Extra notes (limits, quirks). */
    val notes: List<String> = emptyList(),
) {
    /** The layout split into code units; each unit is a list of cells from the high bits to the low bits. */
    val words: List<List<LayoutCell>> get() = InstructionFormats.parseLayout(layout)
    val byteSize: Int get() = codeUnits * 2
}

object InstructionFormats {

    private const val ZERO = 'Ø'

    /** Parses a layout like "B|A|op CCCC" into code units of cells (high bits first). */
    fun parseLayout(layout: String): List<List<LayoutCell>> =
        layout.trim().split(Regex("\\s+")).map { word ->
            word.split('|').map { part ->
                val bits = when {
                    part == "op" -> 8
                    part.all { it == ZERO } -> part.length * 4
                    part.endsWith("lo") || part.endsWith("hi") -> 16
                    else -> part.length * 4
                }
                LayoutCell(part, bits)
            }
        }

    fun byId(id: String): InstructionFormat? = all.firstOrNull { it.id == id }

    /** Short explanation of any format id by decoding its letters, e.g. "21t" -> "2 code units, 1 register, branch target". */
    fun explainId(id: String): String? {
        if (id.length !in 3..4) return null
        val units = id[0].digitToIntOrNull() ?: return null
        val regs = when {
            id[1] == 'r' -> "a range of registers"
            id[1].isDigit() -> id[1].digitToInt().let { if (it == 1) "up to 1 register" else "up to $it registers" }
            else -> return null
        }
        val type = when (id[2]) {
            'b' -> "an immediate signed byte"
            'c' -> "a constant pool index"
            'f' -> "an interface constant (statically linked formats only)"
            'h' -> "an immediate signed hat (high-order bits of a 32/64-bit value, low bits all zero)"
            'i' -> "an immediate 32-bit int or float"
            'l' -> "an immediate 64-bit long or double"
            'm' -> "a method constant (statically linked formats only)"
            'n' -> "an immediate signed nibble"
            's' -> "an immediate signed short"
            't' -> "a branch target"
            'x' -> "no extra data"
            else -> return null
        }
        val suffix = when (id.getOrNull(3)) {
            's' -> "; suggested static-linking variant"
            'i' -> "; suggested inline-linking variant"
            'c' -> "; carries a second constant (e.g. a prototype index)"
            null -> ""
            else -> return null
        }
        return "$units code unit${if (units == 1) "" else "s"}, $regs, $type$suffix"
    }

    private fun f(
        id: String,
        layout: String,
        syntax: List<String>,
        idMeaning: String,
        description: String,
        examples: List<String> = emptyList(),
        optimizerOnly: Boolean = false,
        notes: List<String> = emptyList(),
    ) = InstructionFormat(
        id = id, layout = layout, syntax = syntax,
        codeUnits = parseLayout(layout).size,
        idMeaning = idMeaning, description = description, examples = examples,
        optimizerOnly = optimizerOnly, notes = notes,
    )

    val all: List<InstructionFormat> = listOf(
        f(
            "00x", "$ZERO$ZERO|op", listOf("(none)"),
            "Pseudo-format: no operands, nothing to decode.",
            "A placeholder used for opcode values that are not assigned. The reference page suggests it as the nominal " +
                "format of a breakpoint opcode. No real instruction in a regular DEX file uses it.",
            optimizerOnly = true,
            notes = listOf("Unused opcode values are reserved; a verifier rejects them."),
        ),
        f(
            "10x", "$ZERO$ZERO|op", listOf("op"),
            "1 code unit, 0 registers, no extra data.",
            "Just an opcode in the low byte; the high byte must be zero. Used by instructions that take no operands.",
            examples = listOf("return-void", "nop"),
        ),
        f(
            "12x", "B|A|op", listOf("op vA, vB"),
            "1 code unit, 2 registers, no extra data.",
            "Two 4-bit register numbers share the high byte, so both registers must be v0..v15. The destination " +
                "(A) comes first, then the source (B). Typical for two-register moves, unary operations and the /2addr arithmetic.",
            examples = listOf("move v1, v2", "array-length v0, v1", "add-int/2addr v0, v1"),
            notes = listOf("Registers above v15 cannot be encoded; use the /from16 or /16 move variants to reach them."),
        ),
        f(
            "11n", "B|A|op", listOf("op vA, #+B"),
            "1 code unit, 1 register, an immediate signed nibble.",
            "Register A (4 bits) plus a signed 4-bit literal B, so the literal can only be -8..7.",
            examples = listOf("const/4 v0, 0x1", "const/4 v1, -0x1"),
        ),
        f(
            "11x", "AA|op", listOf("op vAA"),
            "1 code unit, 1 register, no extra data.",
            "A single 8-bit register number, v0..v255. Used by returns, move-result*, move-exception, throw and monitor instructions.",
            examples = listOf("return v0", "move-result-object v1", "throw v0", "monitor-enter v2"),
        ),
        f(
            "10t", "AA|op", listOf("op +AA"),
            "1 code unit, 0 registers, a branch target.",
            "A signed 8-bit offset counted in 16-bit code units, relative to this instruction's own address. " +
                "Smali writes it as a label.",
            examples = listOf("goto :goto_0"),
            notes = listOf("Reach is only about -128..+127 code units; the assembler picks goto/16 or goto/32 for farther targets."),
        ),
        f(
            "20t", "$ZERO$ZERO|op AAAA", listOf("op +AAAA"),
            "2 code units, 0 registers, a branch target.",
            "Like 10t but with a signed 16-bit offset (in code units) in the second unit; the first unit's high byte must be zero.",
            examples = listOf("goto/16 :goto_1"),
        ),
        f(
            "20bc", "AA|op BBBB", listOf("op AA, kind@BBBB"),
            "2 code units, no register operands, an immediate byte (A) and a constant index (B).",
            "Suggested by the page as the format for statically determined verification errors: A is the kind of error and " +
                "B indexes a table suited to that error (for example a method reference for a no-such-method error).",
            optimizerOnly = true,
            notes = listOf(
                "Not valid in regular DEX files. It is only a suggestion for tooling that rewrites instructions that are " +
                    "statically known to fail verification.",
            ),
        ),
        f(
            "22x", "AA|op BBBB", listOf("op vAA, vBBBB"),
            "2 code units, 2 registers, no extra data.",
            "An 8-bit destination (v0..v255) and a 16-bit source (v0..v65535). This is what the /from16 moves use.",
            examples = listOf("move/from16 v0, v300", "move-object/from16 v1, v1000"),
        ),
        f(
            "21t", "AA|op BBBB", listOf("op vAA, +BBBB"),
            "2 code units, 1 register, a branch target.",
            "One 8-bit register tested against zero/null, plus a signed 16-bit branch offset in code units.",
            examples = listOf("if-eqz v0, :cond_0", "if-ltz v1, :cond_1"),
        ),
        f(
            "21s", "AA|op BBBB", listOf("op vAA, #+BBBB"),
            "2 code units, 1 register, an immediate signed short.",
            "An 8-bit register and a signed 16-bit literal (-32768..32767). The wide variant sign-extends the literal to 64 bits.",
            examples = listOf("const/16 v0, 0x100", "const-wide/16 v0, -0x1L"),
        ),
        f(
            "21h", "AA|op BBBB", listOf("op vAA, #+BBBB0000", "op vAA, #+BBBB000000000000"),
            "2 code units, 1 register, an immediate signed hat (high-order bits only).",
            "The 16-bit literal becomes the top 16 bits of a 32-bit value (const/high16) or the top 16 bits of a 64-bit " +
                "value (const-wide/high16); every lower bit is zero. Handy for floats and doubles such as 1.0f or 2.0.",
            examples = listOf("const/high16 v0, 0x3f800000", "const-wide/high16 v0, 0x4000000000000000L"),
            notes = listOf("In smali you write the whole value, not just the 16 stored bits; the low bits must be zero."),
        ),
        f(
            "21c", "AA|op BBBB", listOf(
                "op vAA, type@BBBB", "op vAA, field@BBBB", "op vAA, method_handle@BBBB",
                "op vAA, proto@BBBB", "op vAA, string@BBBB",
            ),
            "2 code units, 1 register, a 16-bit constant pool index.",
            "An 8-bit register plus a 16-bit index into one constant pool; each opcode that uses this format allows exactly one " +
                "kind of constant (string, type, field, method handle or prototype).",
            examples = listOf(
                "const-string v0, \"hello\"", "const-class v0, Ljava/lang/String;", "check-cast v0, Ljava/lang/String;",
                "new-instance v0, Ljava/lang/StringBuilder;", "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
            ),
            notes = listOf("Indexes above 65535 need const-string/jumbo (31c) for strings."),
        ),
        f(
            "23x", "AA|op CC|BB", listOf("op vAA, vBB, vCC"),
            "2 code units, 3 registers, no extra data.",
            "Three 8-bit registers (v0..v255): destination first, then the two sources. Used by three-address arithmetic, " +
                "comparisons and array element access.",
            examples = listOf("add-int v0, v1, v2", "aget v0, v1, v2", "cmp-long v0, v1, v3"),
        ),
        f(
            "22b", "AA|op CC|BB", listOf("op vAA, vBB, #+CC"),
            "2 code units, 2 registers, an immediate signed byte.",
            "Two 8-bit registers and a signed 8-bit literal (-128..127). Used by the /lit8 arithmetic forms.",
            examples = listOf("add-int/lit8 v0, v1, 0x5", "rsub-int/lit8 v0, v1, 0x1"),
        ),
        f(
            "22t", "B|A|op CCCC", listOf("op vA, vB, +CCCC"),
            "2 code units, 2 registers, a branch target.",
            "Two 4-bit registers (v0..v15) compared with each other and a signed 16-bit branch offset in code units.",
            examples = listOf("if-eq v0, v1, :cond_0", "if-lt v2, v3, :cond_1"),
        ),
        f(
            "22s", "B|A|op CCCC", listOf("op vA, vB, #+CCCC"),
            "2 code units, 2 registers, an immediate signed short.",
            "Two 4-bit registers and a signed 16-bit literal. Used by the /lit16 arithmetic forms.",
            examples = listOf("add-int/lit16 v0, v1, 0x100", "mul-int/lit16 v0, v1, 0x400"),
        ),
        f(
            "22c", "B|A|op CCCC", listOf("op vA, vB, type@CCCC", "op vA, vB, field@CCCC"),
            "2 code units, 2 registers, a 16-bit constant pool index.",
            "Two 4-bit registers (v0..v15) plus a type or field index. Instance field access and the type-testing / array-creating " +
                "instructions use it.",
            examples = listOf(
                "iget v0, p0, Lcom/acme/demo/Foo;->count:I", "iput-object v1, p0, Lcom/acme/demo/Foo;->name:Ljava/lang/String;",
                "instance-of v0, v1, Ljava/lang/String;", "new-array v0, v1, [I",
            ),
            notes = listOf("If the object or value lives above v15, copy it into a low register first."),
        ),
        f(
            "22cs", "B|A|op CCCC", listOf("op vA, vB, fieldoff@CCCC"),
            "2 code units, 2 registers, a field offset (statically linked variant of 22c).",
            "A suggested form for statically linked instance field access: the constant pool field index is replaced by a " +
                "prelinked byte offset into the object.",
            optimizerOnly = true,
            notes = listOf("Not valid in regular DEX files; it only appears as an optimizer or ODEX-style rewrite."),
        ),
        f(
            "30t", "$ZERO$ZERO|op AAAAlo AAAAhi", listOf("op +AAAAAAAA"),
            "3 code units, 0 registers, a branch target.",
            "A signed 32-bit branch offset (in code units) split across two units, low half first.",
            examples = listOf("goto/32 :goto_2"),
        ),
        f(
            "32x", "$ZERO$ZERO|op AAAA BBBB", listOf("op vAAAA, vBBBB"),
            "3 code units, 2 registers, no extra data.",
            "Two full 16-bit register numbers (v0..v65535). This is the widest register addressing available for moves.",
            examples = listOf("move/16 v300, v400", "move-wide/16 v256, v512"),
        ),
        f(
            "31i", "AA|op BBBBlo BBBBhi", listOf("op vAA, #+BBBBBBBB"),
            "3 code units, 1 register, an immediate 32-bit int (or float).",
            "An 8-bit register and a 32-bit literal split into two units, low half first. const uses it as-is; const-wide/32 " +
                "sign-extends it to 64 bits.",
            examples = listOf("const v0, 0x12345678", "const-wide/32 v0, 0x7fffffffL"),
        ),
        f(
            "31t", "AA|op BBBBlo BBBBhi", listOf("op vAA, +BBBBBBBB"),
            "3 code units, 1 register, a branch target.",
            "A register and a signed 32-bit offset in code units to a data table that follows elsewhere in the method " +
                "(switch tables and fill-array-data payloads).",
            examples = listOf("packed-switch v0, :pswitch_data_0", "sparse-switch v0, :sswitch_data_0", "fill-array-data v0, :array_0"),
            notes = listOf("The branch target here is a payload, not code: the offset points at the table's ident unit."),
        ),
        f(
            "31c", "AA|op BBBBlo BBBBhi", listOf("op vAA, string@BBBBBBBB"),
            "3 code units, 1 register, a 32-bit constant pool index.",
            "Like 21c but with a 32-bit index, so it can reach strings beyond the first 65536 entries.",
            examples = listOf("const-string/jumbo v0, \"a string from a large pool\""),
        ),
        f(
            "35c", "A|G|op BBBB F|E|D|C",
            listOf(
                "[A=5] op {vC, vD, vE, vF, vG}, meth@BBBB", "[A=5] op {vC, vD, vE, vF, vG}, site@BBBB",
                "[A=5] op {vC, vD, vE, vF, vG}, type@BBBB", "[A=4] op {vC, vD, vE, vF}, kind@BBBB",
                "[A=3] op {vC, vD, vE}, kind@BBBB", "[A=2] op {vC, vD}, kind@BBBB",
                "[A=1] op {vC}, kind@BBBB", "[A=0] op {}, kind@BBBB",
            ),
            "3 code units, up to 5 registers, a 16-bit constant pool index.",
            "The argument-list format: the top nibble A is the number of registers (0..5), each of C, D, E, F and G is a 4-bit " +
                "register number (v0..v15), and B indexes a method, call site or type. The unusual lettering keeps the count and " +
                "index named like the range format 3rc.",
            examples = listOf(
                "invoke-virtual {v0, v1}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z",
                "invoke-static {v0}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;",
                "filled-new-array {v0, v1, v2}, [I",
            ),
            notes = listOf(
                "Used by invoke-virtual/super/direct/static/interface, invoke-custom and filled-new-array.",
                "Every argument register must be v0..v15 and a long/double argument occupies two of the five slots; use the range form otherwise.",
            ),
        ),
        f(
            "35ms", "A|G|op BBBB F|E|D|C",
            listOf("[A=5] op {vC, vD, vE, vF, vG}, vtaboff@BBBB", "[A=1] op {vC}, vtaboff@BBBB"),
            "3 code units, up to 5 registers, a vtable offset (statically linked variant of 35c).",
            "A suggested form for statically linked invoke-virtual and invoke-super: the method index is replaced by a prelinked " +
                "vtable offset.",
            optimizerOnly = true,
            notes = listOf("Not valid in regular DEX files; only produced when an optimizer links calls statically."),
        ),
        f(
            "35mi", "A|G|op BBBB F|E|D|C",
            listOf("[A=5] op {vC, vD, vE, vF, vG}, inline@BBBB", "[A=1] op {vC}, inline@BBBB"),
            "3 code units, up to 5 registers, an inline-table index (inline-linked variant of 35c).",
            "A suggested form for inline-linked invoke-static and invoke-virtual: the method index is replaced by an index into " +
                "a table of natively implemented inline operations, tied closely to a particular VM implementation.",
            optimizerOnly = true,
            notes = listOf("Not valid in regular DEX files."),
        ),
        f(
            "3rc", "AA|op BBBB CCCC",
            listOf("op {vCCCC .. vNNNN}, meth@BBBB", "op {vCCCC .. vNNNN}, site@BBBB", "op {vCCCC .. vNNNN}, type@BBBB"),
            "3 code units, a range of registers, a 16-bit constant pool index.",
            "The /range argument format: AA (8 bits) is the register count 0..255, CCCC is the first register (16 bits), and the " +
                "registers are consecutive, ending at NNNN = CCCC + AA - 1. B indexes a method, call site or type.",
            examples = listOf(
                "invoke-static/range {v0 .. v5}, Lcom/acme/demo/Foo;->sum(IIIIII)V",
                "filled-new-array/range {v0 .. v7}, [I",
            ),
            notes = listOf("Wide arguments count as two registers each in the range."),
        ),
        f(
            "3rms", "AA|op BBBB CCCC", listOf("op {vCCCC .. vNNNN}, vtaboff@BBBB"),
            "3 code units, a range of registers, a vtable offset (statically linked variant of 3rc).",
            "A suggested form for statically linked invoke-virtual/range and invoke-super/range, with a prelinked vtable offset " +
                "instead of a method index.",
            optimizerOnly = true,
            notes = listOf("Not valid in regular DEX files."),
        ),
        f(
            "3rmi", "AA|op BBBB CCCC", listOf("op {vCCCC .. vNNNN}, inline@BBBB"),
            "3 code units, a range of registers, an inline-table index (inline-linked variant of 3rc).",
            "A suggested form for inline-linked invoke-static/range and invoke-virtual/range, with an inline-table index " +
                "instead of a method index.",
            optimizerOnly = true,
            notes = listOf("Not valid in regular DEX files."),
        ),
        f(
            "45cc", "A|G|op BBBB F|E|D|C HHHH",
            listOf(
                "[A=5] op {vC, vD, vE, vF, vG}, meth@BBBB, proto@HHHH", "[A=4] op {vC, vD, vE, vF}, meth@BBBB, proto@HHHH",
                "[A=3] op {vC, vD, vE}, meth@BBBB, proto@HHHH", "[A=2] op {vC, vD}, meth@BBBB, proto@HHHH",
                "[A=1] op {vC}, meth@BBBB, proto@HHHH",
            ),
            "4 code units, up to 5 registers, a method index and a second constant (prototype index).",
            "35c with an extra 16-bit prototype index H. Used by invoke-polymorphic, where the call site supplies the exact " +
                "method prototype that the signature-polymorphic method (such as MethodHandle.invoke) is invoked with.",
            examples = listOf("invoke-polymorphic {v0, v1}, Ljava/lang/invoke/MethodHandle;->invoke([Ljava/lang/Object;)Ljava/lang/Object;, (I)V"),
            notes = listOf("Introduced with DEX version 038 (Android 8.0)."),
        ),
        f(
            "4rcc", "AA|op BBBB CCCC HHHH", listOf("op {vCCCC .. vNNNN}, meth@BBBB, proto@HHHH"),
            "4 code units, a range of registers, a method index and a second constant (prototype index).",
            "The /range counterpart of 45cc: AA is the register count, CCCC the first register, B the method and H the prototype.",
            examples = listOf("invoke-polymorphic/range {v0 .. v3}, Ljava/lang/invoke/MethodHandle;->invoke([Ljava/lang/Object;)Ljava/lang/Object;, (III)V"),
            notes = listOf("Introduced with DEX version 038 (Android 8.0)."),
        ),
        f(
            "51l", "AA|op BBBBlo BBBB BBBB BBBBhi", listOf("op vAA, #+BBBBBBBBBBBBBBBB"),
            "5 code units, 1 register, an immediate 64-bit long (or double).",
            "The longest format: an 8-bit register and a full 64-bit literal spread over four units, low part first. " +
                "Only const-wide uses it.",
            examples = listOf("const-wide v0, 0x123456789abcdefL", "const-wide v0, 0x400921fb54442d18L"),
        ),
    )
}
