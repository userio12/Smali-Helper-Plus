package com.riyadm.smalihelperplus.core.smali.opcodes

/**
 * Dalvik instruction formats, as documented in
 * https://source.android.com/docs/core/runtime/instruction-formats.
 *
 * [units] is the size of the instruction in 16-bit code units. [syntax] is the format's syntax column.
 * Only formats used by assignable opcodes are listed (the odex-only formats 20bc, 22cs, 35mi, 35ms, 3rmi and 3rms are
 * not valid in regular dex files and are therefore not modelled).
 */
enum class InstructionFormat(val id: String, val units: Int, val syntax: String) {
    F10x("10x", 1, "op"),
    F12x("12x", 1, "op vA, vB"),
    F11n("11n", 1, "op vA, #+B"),
    F11x("11x", 1, "op vAA"),
    F10t("10t", 1, "op +AA"),
    F20t("20t", 2, "op +AAAA"),
    F22x("22x", 2, "op vAA, vBBBB"),
    F21t("21t", 2, "op vAA, +BBBB"),
    F21s("21s", 2, "op vAA, #+BBBB"),
    F21h("21h", 2, "op vAA, #+BBBB0000 / op vAA, #+BBBB000000000000"),
    F21c("21c", 2, "op vAA, kind@BBBB"),
    F23x("23x", 2, "op vAA, vBB, vCC"),
    F22b("22b", 2, "op vAA, vBB, #+CC"),
    F22t("22t", 2, "op vA, vB, +CCCC"),
    F22s("22s", 2, "op vA, vB, #+CCCC"),
    F22c("22c", 2, "op vA, vB, kind@CCCC"),
    F30t("30t", 3, "op +AAAAAAAA"),
    F32x("32x", 3, "op vAAAA, vBBBB"),
    F31i("31i", 3, "op vAA, #+BBBBBBBB"),
    F31t("31t", 3, "op vAA, +BBBBBBBB"),
    F31c("31c", 3, "op vAA, string@BBBBBBBB"),
    F35c("35c", 3, "op {vC, vD, vE, vF, vG}, kind@BBBB"),
    F3rc("3rc", 3, "op {vCCCC .. vNNNN}, kind@BBBB"),
    F45cc("45cc", 4, "op {vC, vD, vE, vF, vG}, meth@BBBB, proto@HHHH"),
    F4rcc("4rcc", 4, "op {vCCCC .. vNNNN}, meth@BBBB, proto@HHHH"),
    F51l("51l", 5, "op vAA, #+BBBBBBBBBBBBBBBB"),
    ;

    companion object {
        fun byId(id: String): InstructionFormat? = entries.firstOrNull { it.id == id }
    }
}

/** How an instruction uses a register operand. */
enum class Access { READ, WRITE, READ_WRITE }

/** Kind of constant-pool reference carried by an instruction. */
enum class ReferenceKind(val label: String) {
    STRING("string"),
    TYPE("type"),
    FIELD("field"),
    METHOD("method"),
    CALL_SITE("call site"),
    METHOD_HANDLE("method handle"),
    PROTO("method prototype"),
}

/**
 * How a literal is interpreted.
 * - [SIGNED]: sign-extended literal of [OperandSpec.Literal.bits] bits.
 * - [HIGH16]: 32-bit value whose low 16 bits must be zero (`const/high16`).
 * - [WIDE_HIGH16]: 64-bit value whose low 48 bits must be zero (`const-wide/high16`).
 * - [ARBITRARY]: any bit pattern of [OperandSpec.Literal.bits] bits (`const`, `const-wide`).
 */
enum class LiteralKind { SIGNED, HIGH16, WIDE_HIGH16, ARBITRARY }

/** Payload pseudo-instruction targeted by a 31t instruction. */
enum class PayloadKind(val directive: String) {
    PACKED_SWITCH(".packed-switch"),
    SPARSE_SWITCH(".sparse-switch"),
    ARRAY_DATA(".array-data"),
}

/** Shape of one operand of an instruction. */
sealed interface OperandSpec {
    /** A single register (or register pair when [wide]) addressable with [bits] bits. */
    data class Register(val bits: Int, val access: Access, val wide: Boolean = false, val role: String) : OperandSpec {
        val maxRegister: Int get() = (1 shl bits) - 1
    }

    /** An inline literal of [bits] bits. [wide] is true when the destination is a 64-bit register pair. */
    data class Literal(val bits: Int, val kind: LiteralKind, val wide: Boolean = false) : OperandSpec

    /** A constant-pool reference (index of [bits] bits). */
    data class Reference(val kind: ReferenceKind, val bits: Int = 16) : OperandSpec

    /** Signed branch offset of [bits] bits, written as a label in smali. */
    data class BranchTarget(val bits: Int) : OperandSpec

    /** 32-bit signed offset to a payload pseudo-instruction, written as a label in smali. */
    data class PayloadTarget(val kind: PayloadKind) : OperandSpec

    /** Explicit list of up to [max] 4-bit argument registers, e.g. `{v0, v1}`. */
    data class RegisterList(val max: Int = 5) : OperandSpec

    /** Contiguous register range with a 16-bit first register and up to 255 registers, e.g. `{v0 .. v5}`. */
    data object RegisterRange : OperandSpec
}

/** How control leaves an instruction. */
enum class FlowKind { NORMAL, GOTO, CONDITIONAL, SWITCH, RETURN, THROW }

/** Broad family used by the explainer and UI grouping. */
enum class OpcodeFamily {
    NOP, MOVE, MOVE_RESULT, MOVE_EXCEPTION, RETURN, CONST, CONST_STRING, CONST_CLASS, MONITOR, CHECK_CAST, INSTANCE_OF,
    ARRAY_LENGTH, NEW_INSTANCE, NEW_ARRAY, FILLED_NEW_ARRAY, FILL_ARRAY_DATA, THROW, GOTO, SWITCH, COMPARE, IF_TEST,
    IF_TESTZ, ARRAY_GET, ARRAY_PUT, INSTANCE_GET, INSTANCE_PUT, STATIC_GET, STATIC_PUT, INVOKE, UNARY, CONVERSION,
    BINARY, BINARY_2ADDR, BINARY_LIT16, BINARY_LIT8, INVOKE_POLYMORPHIC, INVOKE_CUSTOM, CONST_METHOD_HANDLE,
    CONST_METHOD_TYPE,
}

/** One documented Dalvik opcode. */
data class DalvikOpcode(
    val value: Int,
    val mnemonic: String,
    val format: InstructionFormat,
    val operands: List<OperandSpec>,
    val family: OpcodeFamily,
    val flow: FlowKind = FlowKind.NORMAL,
    /** First Android API level whose runtime accepts the opcode (dex 038 = API 26, dex 039 = API 28). */
    val minApi: Int = 1,
    /** True for invoke-kind and filled-new-array, whose result may be read by an immediately following move-result*. */
    val setsResult: Boolean = false,
) {
    val hexValue: String get() = "0x%02x".format(value)
    val isRange: Boolean get() = operands.any { it is OperandSpec.RegisterRange }
    val isInvoke: Boolean
        get() = family == OpcodeFamily.INVOKE || family == OpcodeFamily.INVOKE_POLYMORPHIC ||
            family == OpcodeFamily.INVOKE_CUSTOM
    val registerOperands: List<OperandSpec.Register> get() = operands.filterIsInstance<OperandSpec.Register>()
}

/**
 * Every assigned opcode of the Dalvik instruction set (0x00..0xff without the unused ranges 3e..43, 73, 79..7a and
 * e3..f9), derived from https://source.android.com/docs/core/runtime/dalvik-bytecode.
 */
object DalvikOpcodeTable {
    private fun w(bits: Int, wide: Boolean = false, role: String = "destination") =
        OperandSpec.Register(bits, Access.WRITE, wide, role)

    private fun r(bits: Int, wide: Boolean = false, role: String = "source") =
        OperandSpec.Register(bits, Access.READ, wide, role)

    private fun rw(bits: Int, wide: Boolean = false, role: String = "destination and first source") =
        OperandSpec.Register(bits, Access.READ_WRITE, wide, role)

    private fun ref(kind: ReferenceKind, bits: Int = 16) = OperandSpec.Reference(kind, bits)

    private val list = ArrayList<DalvikOpcode>(224)

    private fun add(
        value: Int,
        mnemonic: String,
        format: InstructionFormat,
        family: OpcodeFamily,
        vararg operands: OperandSpec,
        flow: FlowKind = FlowKind.NORMAL,
        minApi: Int = 1,
        setsResult: Boolean = false,
    ) {
        list += DalvikOpcode(value, mnemonic, format, operands.toList(), family, flow, minApi, setsResult)
    }

    init {
        add(0x00, "nop", InstructionFormat.F10x, OpcodeFamily.NOP)

        add(0x01, "move", InstructionFormat.F12x, OpcodeFamily.MOVE, w(4), r(4))
        add(0x02, "move/from16", InstructionFormat.F22x, OpcodeFamily.MOVE, w(8), r(16))
        add(0x03, "move/16", InstructionFormat.F32x, OpcodeFamily.MOVE, w(16), r(16))
        add(0x04, "move-wide", InstructionFormat.F12x, OpcodeFamily.MOVE, w(4, true), r(4, true))
        add(0x05, "move-wide/from16", InstructionFormat.F22x, OpcodeFamily.MOVE, w(8, true), r(16, true))
        add(0x06, "move-wide/16", InstructionFormat.F32x, OpcodeFamily.MOVE, w(16, true), r(16, true))
        add(0x07, "move-object", InstructionFormat.F12x, OpcodeFamily.MOVE, w(4), r(4))
        add(0x08, "move-object/from16", InstructionFormat.F22x, OpcodeFamily.MOVE, w(8), r(16))
        add(0x09, "move-object/16", InstructionFormat.F32x, OpcodeFamily.MOVE, w(16), r(16))

        add(0x0a, "move-result", InstructionFormat.F11x, OpcodeFamily.MOVE_RESULT, w(8))
        add(0x0b, "move-result-wide", InstructionFormat.F11x, OpcodeFamily.MOVE_RESULT, w(8, true))
        add(0x0c, "move-result-object", InstructionFormat.F11x, OpcodeFamily.MOVE_RESULT, w(8))
        add(0x0d, "move-exception", InstructionFormat.F11x, OpcodeFamily.MOVE_EXCEPTION, w(8))

        add(0x0e, "return-void", InstructionFormat.F10x, OpcodeFamily.RETURN, flow = FlowKind.RETURN)
        add(0x0f, "return", InstructionFormat.F11x, OpcodeFamily.RETURN, r(8, role = "return value"), flow = FlowKind.RETURN)
        add(0x10, "return-wide", InstructionFormat.F11x, OpcodeFamily.RETURN, r(8, true, "return value"), flow = FlowKind.RETURN)
        add(0x11, "return-object", InstructionFormat.F11x, OpcodeFamily.RETURN, r(8, role = "return value"), flow = FlowKind.RETURN)

        add(0x12, "const/4", InstructionFormat.F11n, OpcodeFamily.CONST, w(4), OperandSpec.Literal(4, LiteralKind.SIGNED))
        add(0x13, "const/16", InstructionFormat.F21s, OpcodeFamily.CONST, w(8), OperandSpec.Literal(16, LiteralKind.SIGNED))
        add(0x14, "const", InstructionFormat.F31i, OpcodeFamily.CONST, w(8), OperandSpec.Literal(32, LiteralKind.ARBITRARY))
        add(0x15, "const/high16", InstructionFormat.F21h, OpcodeFamily.CONST, w(8), OperandSpec.Literal(32, LiteralKind.HIGH16))
        add(0x16, "const-wide/16", InstructionFormat.F21s, OpcodeFamily.CONST, w(8, true), OperandSpec.Literal(16, LiteralKind.SIGNED, true))
        add(0x17, "const-wide/32", InstructionFormat.F31i, OpcodeFamily.CONST, w(8, true), OperandSpec.Literal(32, LiteralKind.SIGNED, true))
        add(0x18, "const-wide", InstructionFormat.F51l, OpcodeFamily.CONST, w(8, true), OperandSpec.Literal(64, LiteralKind.ARBITRARY, true))
        add(0x19, "const-wide/high16", InstructionFormat.F21h, OpcodeFamily.CONST, w(8, true), OperandSpec.Literal(64, LiteralKind.WIDE_HIGH16, true))
        add(0x1a, "const-string", InstructionFormat.F21c, OpcodeFamily.CONST_STRING, w(8), ref(ReferenceKind.STRING))
        add(0x1b, "const-string/jumbo", InstructionFormat.F31c, OpcodeFamily.CONST_STRING, w(8), ref(ReferenceKind.STRING, 32))
        add(0x1c, "const-class", InstructionFormat.F21c, OpcodeFamily.CONST_CLASS, w(8), ref(ReferenceKind.TYPE))

        add(0x1d, "monitor-enter", InstructionFormat.F11x, OpcodeFamily.MONITOR, r(8, role = "object"))
        add(0x1e, "monitor-exit", InstructionFormat.F11x, OpcodeFamily.MONITOR, r(8, role = "object"))
        add(0x1f, "check-cast", InstructionFormat.F21c, OpcodeFamily.CHECK_CAST, r(8, role = "object"), ref(ReferenceKind.TYPE))
        add(0x20, "instance-of", InstructionFormat.F22c, OpcodeFamily.INSTANCE_OF, w(4), r(4, role = "object"), ref(ReferenceKind.TYPE))
        add(0x21, "array-length", InstructionFormat.F12x, OpcodeFamily.ARRAY_LENGTH, w(4), r(4, role = "array"))
        add(0x22, "new-instance", InstructionFormat.F21c, OpcodeFamily.NEW_INSTANCE, w(8), ref(ReferenceKind.TYPE))
        add(0x23, "new-array", InstructionFormat.F22c, OpcodeFamily.NEW_ARRAY, w(4), r(4, role = "size"), ref(ReferenceKind.TYPE))
        add(0x24, "filled-new-array", InstructionFormat.F35c, OpcodeFamily.FILLED_NEW_ARRAY, OperandSpec.RegisterList(), ref(ReferenceKind.TYPE), setsResult = true)
        add(0x25, "filled-new-array/range", InstructionFormat.F3rc, OpcodeFamily.FILLED_NEW_ARRAY, OperandSpec.RegisterRange, ref(ReferenceKind.TYPE), setsResult = true)
        add(0x26, "fill-array-data", InstructionFormat.F31t, OpcodeFamily.FILL_ARRAY_DATA, r(8, role = "array"), OperandSpec.PayloadTarget(PayloadKind.ARRAY_DATA))
        add(0x27, "throw", InstructionFormat.F11x, OpcodeFamily.THROW, r(8, role = "exception"), flow = FlowKind.THROW)

        add(0x28, "goto", InstructionFormat.F10t, OpcodeFamily.GOTO, OperandSpec.BranchTarget(8), flow = FlowKind.GOTO)
        add(0x29, "goto/16", InstructionFormat.F20t, OpcodeFamily.GOTO, OperandSpec.BranchTarget(16), flow = FlowKind.GOTO)
        add(0x2a, "goto/32", InstructionFormat.F30t, OpcodeFamily.GOTO, OperandSpec.BranchTarget(32), flow = FlowKind.GOTO)
        add(0x2b, "packed-switch", InstructionFormat.F31t, OpcodeFamily.SWITCH, r(8, role = "tested value"), OperandSpec.PayloadTarget(PayloadKind.PACKED_SWITCH), flow = FlowKind.SWITCH)
        add(0x2c, "sparse-switch", InstructionFormat.F31t, OpcodeFamily.SWITCH, r(8, role = "tested value"), OperandSpec.PayloadTarget(PayloadKind.SPARSE_SWITCH), flow = FlowKind.SWITCH)

        add(0x2d, "cmpl-float", InstructionFormat.F23x, OpcodeFamily.COMPARE, w(8), r(8), r(8))
        add(0x2e, "cmpg-float", InstructionFormat.F23x, OpcodeFamily.COMPARE, w(8), r(8), r(8))
        add(0x2f, "cmpl-double", InstructionFormat.F23x, OpcodeFamily.COMPARE, w(8), r(8, true), r(8, true))
        add(0x30, "cmpg-double", InstructionFormat.F23x, OpcodeFamily.COMPARE, w(8), r(8, true), r(8, true))
        add(0x31, "cmp-long", InstructionFormat.F23x, OpcodeFamily.COMPARE, w(8), r(8, true), r(8, true))

        listOf("if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le").forEachIndexed { i, name ->
            add(0x32 + i, name, InstructionFormat.F22t, OpcodeFamily.IF_TEST, r(4, role = "first operand"), r(4, role = "second operand"), OperandSpec.BranchTarget(16), flow = FlowKind.CONDITIONAL)
        }
        listOf("if-eqz", "if-nez", "if-ltz", "if-gez", "if-gtz", "if-lez").forEachIndexed { i, name ->
            add(0x38 + i, name, InstructionFormat.F21t, OpcodeFamily.IF_TESTZ, r(8, role = "tested value"), OperandSpec.BranchTarget(16), flow = FlowKind.CONDITIONAL)
        }

        val suffixes = listOf("", "-wide", "-object", "-boolean", "-byte", "-char", "-short")
        suffixes.forEachIndexed { i, s ->
            add(0x44 + i, "aget$s", InstructionFormat.F23x, OpcodeFamily.ARRAY_GET, w(8, s == "-wide", "value"), r(8, role = "array"), r(8, role = "index"))
        }
        suffixes.forEachIndexed { i, s ->
            add(0x4b + i, "aput$s", InstructionFormat.F23x, OpcodeFamily.ARRAY_PUT, r(8, s == "-wide", "value"), r(8, role = "array"), r(8, role = "index"))
        }
        suffixes.forEachIndexed { i, s ->
            add(0x52 + i, "iget$s", InstructionFormat.F22c, OpcodeFamily.INSTANCE_GET, w(4, s == "-wide", "value"), r(4, role = "object"), ref(ReferenceKind.FIELD))
        }
        suffixes.forEachIndexed { i, s ->
            add(0x59 + i, "iput$s", InstructionFormat.F22c, OpcodeFamily.INSTANCE_PUT, r(4, s == "-wide", "value"), r(4, role = "object"), ref(ReferenceKind.FIELD))
        }
        suffixes.forEachIndexed { i, s ->
            add(0x60 + i, "sget$s", InstructionFormat.F21c, OpcodeFamily.STATIC_GET, w(8, s == "-wide", "value"), ref(ReferenceKind.FIELD))
        }
        suffixes.forEachIndexed { i, s ->
            add(0x67 + i, "sput$s", InstructionFormat.F21c, OpcodeFamily.STATIC_PUT, r(8, s == "-wide", "value"), ref(ReferenceKind.FIELD))
        }

        val invokeKinds = listOf("virtual", "super", "direct", "static", "interface")
        invokeKinds.forEachIndexed { i, k ->
            add(0x6e + i, "invoke-$k", InstructionFormat.F35c, OpcodeFamily.INVOKE, OperandSpec.RegisterList(), ref(ReferenceKind.METHOD), setsResult = true)
        }
        invokeKinds.forEachIndexed { i, k ->
            add(0x74 + i, "invoke-$k/range", InstructionFormat.F3rc, OpcodeFamily.INVOKE, OperandSpec.RegisterRange, ref(ReferenceKind.METHOD), setsResult = true)
        }

        // Unary operations and conversions: (name, destination wide, source wide)
        val unary = listOf(
            Triple("neg-int", false, false), Triple("not-int", false, false), Triple("neg-long", true, true),
            Triple("not-long", true, true), Triple("neg-float", false, false), Triple("neg-double", true, true),
            Triple("int-to-long", true, false), Triple("int-to-float", false, false), Triple("int-to-double", true, false),
            Triple("long-to-int", false, true), Triple("long-to-float", false, true), Triple("long-to-double", true, true),
            Triple("float-to-int", false, false), Triple("float-to-long", true, false), Triple("float-to-double", true, false),
            Triple("double-to-int", false, true), Triple("double-to-long", true, true), Triple("double-to-float", false, true),
            Triple("int-to-byte", false, false), Triple("int-to-char", false, false), Triple("int-to-short", false, false),
        )
        unary.forEachIndexed { i, (name, dw, sw) ->
            val family = if (name.contains("-to-")) OpcodeFamily.CONVERSION else OpcodeFamily.UNARY
            add(0x7b + i, name, InstructionFormat.F12x, family, w(4, dw), r(4, sw))
        }

        val binOps = listOf("add", "sub", "mul", "div", "rem", "and", "or", "xor", "shl", "shr", "ushr")
        val floatOps = listOf("add", "sub", "mul", "div", "rem")
        // binop vAA, vBB, vCC (0x90..0xaf) and binop/2addr vA, vB (0xb0..0xcf) share the same ordering.
        val binaries = ArrayList<Triple<String, Boolean, Boolean>>() // name, operands wide, second source wide
        binOps.forEach { binaries += Triple("$it-int", false, false) }
        binOps.forEach { binaries += Triple("$it-long", true, it !in setOf("shl", "shr", "ushr")) }
        floatOps.forEach { binaries += Triple("$it-float", false, false) }
        floatOps.forEach { binaries += Triple("$it-double", true, true) }
        binaries.forEachIndexed { i, (name, wide, secondWide) ->
            add(0x90 + i, name, InstructionFormat.F23x, OpcodeFamily.BINARY, w(8, wide), r(8, wide, "first source"), r(8, secondWide, "second source"))
        }
        binaries.forEachIndexed { i, (name, wide, secondWide) ->
            add(0xb0 + i, "$name/2addr", InstructionFormat.F12x, OpcodeFamily.BINARY_2ADDR, rw(4, wide), r(4, secondWide, "second source"))
        }

        listOf("add-int/lit16", "rsub-int", "mul-int/lit16", "div-int/lit16", "rem-int/lit16", "and-int/lit16", "or-int/lit16", "xor-int/lit16")
            .forEachIndexed { i, name ->
                add(0xd0 + i, name, InstructionFormat.F22s, OpcodeFamily.BINARY_LIT16, w(4), r(4), OperandSpec.Literal(16, LiteralKind.SIGNED))
            }
        listOf(
            "add-int/lit8", "rsub-int/lit8", "mul-int/lit8", "div-int/lit8", "rem-int/lit8", "and-int/lit8", "or-int/lit8",
            "xor-int/lit8", "shl-int/lit8", "shr-int/lit8", "ushr-int/lit8",
        ).forEachIndexed { i, name ->
            add(0xd8 + i, name, InstructionFormat.F22b, OpcodeFamily.BINARY_LIT8, w(8), r(8), OperandSpec.Literal(8, LiteralKind.SIGNED))
        }

        add(0xfa, "invoke-polymorphic", InstructionFormat.F45cc, OpcodeFamily.INVOKE_POLYMORPHIC, OperandSpec.RegisterList(), ref(ReferenceKind.METHOD), ref(ReferenceKind.PROTO), minApi = 26, setsResult = true)
        add(0xfb, "invoke-polymorphic/range", InstructionFormat.F4rcc, OpcodeFamily.INVOKE_POLYMORPHIC, OperandSpec.RegisterRange, ref(ReferenceKind.METHOD), ref(ReferenceKind.PROTO), minApi = 26, setsResult = true)
        add(0xfc, "invoke-custom", InstructionFormat.F35c, OpcodeFamily.INVOKE_CUSTOM, OperandSpec.RegisterList(), ref(ReferenceKind.CALL_SITE), minApi = 26, setsResult = true)
        add(0xfd, "invoke-custom/range", InstructionFormat.F3rc, OpcodeFamily.INVOKE_CUSTOM, OperandSpec.RegisterRange, ref(ReferenceKind.CALL_SITE), minApi = 26, setsResult = true)
        add(0xfe, "const-method-handle", InstructionFormat.F21c, OpcodeFamily.CONST_METHOD_HANDLE, w(8), ref(ReferenceKind.METHOD_HANDLE), minApi = 28)
        add(0xff, "const-method-type", InstructionFormat.F21c, OpcodeFamily.CONST_METHOD_TYPE, w(8), ref(ReferenceKind.PROTO), minApi = 28)
    }

    /** All opcodes ordered by opcode value. */
    val all: List<DalvikOpcode> = list.sortedBy { it.value }

    private val byMnemonic: Map<String, DalvikOpcode> = all.associateBy { it.mnemonic }
    private val byValue: Map<Int, DalvikOpcode> = all.associateBy { it.value }

    val mnemonics: List<String> = all.map { it.mnemonic }

    /** Opcode values that the specification marks as unused. */
    val unusedValues: List<Int> = (0x3e..0x43) + 0x73 + (0x79..0x7a) + (0xe3..0xf9)

    fun byMnemonic(mnemonic: String): DalvikOpcode? = byMnemonic[mnemonic]
    fun byValue(value: Int): DalvikOpcode? = byValue[value]

    /** Returns the closest mnemonics by edit distance (for "did you mean" suggestions). */
    fun suggest(mnemonic: String, max: Int = 3): List<String> {
        val target = mnemonic.lowercase()
        val limit = maxOf(2, target.length / 3)
        return mnemonics
            .map { it to editDistance(target, it) }
            .filter { it.second <= limit }
            .sortedWith(compareBy({ it.second }, { it.first.length }))
            .take(max)
            .map { it.first }
    }

    /** Levenshtein distance. */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
