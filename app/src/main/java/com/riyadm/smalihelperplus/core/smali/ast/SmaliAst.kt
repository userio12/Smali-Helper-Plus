package com.riyadm.smalihelperplus.core.smali.ast

import com.riyadm.smalihelperplus.core.smali.lexer.LexedLine
import com.riyadm.smalihelperplus.core.smali.model.Descriptors
import com.riyadm.smalihelperplus.core.smali.model.MethodProto
import com.riyadm.smalihelperplus.core.smali.opcodes.DalvikOpcode
import com.riyadm.smalihelperplus.core.smali.opcodes.PayloadKind
import java.math.BigInteger

/** Position of a syntax element: 1-based [line], 0-based columns (end exclusive). */
data class SourceRange(val line: Int, val startColumn: Int, val endColumn: Int)

// ---------------------------------------------------------------------------------------------------------------------
// Operands
// ---------------------------------------------------------------------------------------------------------------------

sealed interface Operand {
    val text: String
    val range: SourceRange
}

/** `vN` or `pN`. */
data class RegisterOperand(override val text: String, override val range: SourceRange) : Operand {
    val prefix: Char get() = text[0]
    val number: Int get() = text.substring(1).toIntOrNull() ?: Int.MAX_VALUE
    val isParameter: Boolean get() = prefix == 'p'
}

/** `{v0, v1}` (possibly empty). */
data class RegisterListOperand(
    override val text: String,
    override val range: SourceRange,
    val registers: List<RegisterOperand>,
) : Operand

/** `{v0 .. v3}`. */
data class RegisterRangeOperand(
    override val text: String,
    override val range: SourceRange,
    val first: RegisterOperand,
    val last: RegisterOperand,
) : Operand

/** Kind of literal as written in the source. */
enum class LiteralType { INTEGER, LONG, SHORT, BYTE, FLOAT, DOUBLE, CHAR, BOOLEAN }

/**
 * A numeric/char/boolean literal. [value] is the mathematical value as written (for integers), [radixPrefixed] is
 * true for hexadecimal/octal notation (which smali allows to express two's-complement bit patterns), and
 * [floatValue] is set for floating point literals.
 */
data class LiteralOperand(
    override val text: String,
    override val range: SourceRange,
    val type: LiteralType,
    val value: BigInteger?,
    val radixPrefixed: Boolean,
    val floatValue: Double? = null,
) : Operand {
    val isFloatingPoint: Boolean get() = type == LiteralType.FLOAT || type == LiteralType.DOUBLE
}

/** `"text"` (raw, still escaped). */
data class StringOperand(override val text: String, override val range: SourceRange) : Operand {
    val content: String get() = if (text.length >= 2) text.substring(1, text.length - 1) else ""
}

/** `:label`. */
data class LabelOperand(override val text: String, override val range: SourceRange) : Operand {
    val name: String get() = text.removePrefix(":")
}

/** Anything else: type, field, method, prototype, method handle or call site reference. */
data class ReferenceOperand(override val text: String, override val range: SourceRange) : Operand

// ---------------------------------------------------------------------------------------------------------------------
// Method items
// ---------------------------------------------------------------------------------------------------------------------

sealed interface MethodItem {
    val line: Int
}

/** One instruction. [opcode] is null for unknown mnemonics. [index] is the position among the method's instructions. */
data class Instruction(
    val mnemonic: String,
    val opcode: DalvikOpcode?,
    val operands: List<Operand>,
    override val line: Int,
    val mnemonicRange: SourceRange,
    /** Source text of the instruction without comment and surrounding whitespace. */
    val text: String,
    val index: Int,
) : MethodItem {
    val range: SourceRange get() = SourceRange(line, mnemonicRange.startColumn, operands.lastOrNull()?.range?.endColumn ?: mnemonicRange.endColumn)
}

data class LabelDefinition(val name: String, override val line: Int, val range: SourceRange) : MethodItem

/** `.catch Type; {:start .. :end} :handler` or `.catchall {...} :handler` ([exceptionType] null). */
data class CatchDirective(
    val exceptionType: String?,
    val start: LabelOperand,
    val end: LabelOperand,
    val handler: LabelOperand,
    override val line: Int,
    val range: SourceRange,
) : MethodItem

/** `.line`, `.local`, `.end local`, `.restart local`, `.prologue`, `.epilogue`, `.source` inside methods. */
data class DebugDirective(val directive: String, val arguments: String, override val line: Int) : MethodItem

/** `.param p1, "name"` with optional annotation block closed by `.end param`. */
data class ParamDirective(
    val register: RegisterOperand?,
    val name: String?,
    override val line: Int,
    val endLine: Int?,
) : MethodItem

/** `.packed-switch`, `.sparse-switch` or `.array-data` payload. */
data class PayloadDirective(
    val kind: PayloadKind,
    override val line: Int,
    val endLine: Int?,
    /** Label defined directly before the payload (the target of the switch / fill-array-data instruction). */
    val labels: List<String>,
    /** First key of a packed switch. */
    val firstKey: Long? = null,
    /** Sparse switch keys (parallel to [targets]). */
    val keys: List<Long> = emptyList(),
    val targets: List<LabelOperand> = emptyList(),
    /** Element width of array data. */
    val elementWidth: Int? = null,
    val values: List<String> = emptyList(),
) : MethodItem

data class AnnotationBlock(val visibility: String?, val type: String?, val startLine: Int, val endLine: Int?) : MethodItem {
    override val line: Int get() = startLine
}

/** `.registers N` or `.locals N`. */
data class CountDirective(val directive: String, val value: Int?, val line: Int, val range: SourceRange)

// ---------------------------------------------------------------------------------------------------------------------
// Members
// ---------------------------------------------------------------------------------------------------------------------

data class ClassHeader(val flags: List<String>, val descriptor: String, val line: Int, val nameRange: SourceRange)

data class TypeDirective(val descriptor: String, val line: Int, val range: SourceRange)

data class FieldDefinition(
    val flags: List<String>,
    val name: String,
    val type: String,
    val initialValue: String?,
    val line: Int,
    val endLine: Int?,
    val annotations: List<AnnotationBlock>,
    val nameRange: SourceRange,
) {
    val isStatic: Boolean get() = "static" in flags
}

data class MethodDefinition(
    val flags: List<String>,
    val name: String,
    val protoText: String,
    val proto: MethodProto?,
    val startLine: Int,
    /** Line of `.end method`, or null when missing. */
    val endLine: Int?,
    val registers: CountDirective?,
    val locals: CountDirective?,
    val items: List<MethodItem>,
    val nameRange: SourceRange,
    /** True for code snippets that were analysed without a surrounding `.method` (fragment mode). */
    val isImplicit: Boolean = false,
) {
    val instructions: List<Instruction> by lazy(LazyThreadSafetyMode.NONE) { items.filterIsInstance<Instruction>() }
    val isStatic: Boolean get() = "static" in flags
    val isAbstractOrNative: Boolean get() = "abstract" in flags || "native" in flags

    /** Words used by incoming arguments, including the implicit `this` of instance methods. */
    val parameterWords: Int? get() = proto?.let { it.parameterWords + if (isStatic) 0 else 1 }

    /** Total size of the register frame, if determinable. */
    val frameSize: Int?
        get() = registers?.value ?: locals?.value?.let { l -> parameterWords?.let { l + it } }

    /** Number of local (non-parameter) registers, if determinable. */
    val localCount: Int?
        get() = locals?.value ?: registers?.value?.let { r -> parameterWords?.let { r - it } }

    val signature: String get() = name + protoText
}

/** Parsed smali file (or fragment). */
data class SmaliFile(
    val header: ClassHeader?,
    val superClass: TypeDirective?,
    val source: String?,
    val interfaces: List<TypeDirective>,
    val annotations: List<AnnotationBlock>,
    val fields: List<FieldDefinition>,
    val methods: List<MethodDefinition>,
    /** Instructions found outside any method (an error in full-file mode). */
    val strayInstructions: List<Instruction>,
    val lines: List<LexedLine>,
    val isFragment: Boolean,
) {
    val className: String? get() = header?.descriptor
    val javaClassName: String? get() = header?.descriptor?.let { Descriptors.toJavaName(it) }
}
