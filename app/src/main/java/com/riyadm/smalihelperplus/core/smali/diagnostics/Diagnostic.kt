package com.riyadm.smalihelperplus.core.smali.diagnostics

enum class Severity { ERROR, WARNING, INFO }

/**
 * One finding of the analyser. [line] is 1-based, columns are 0-based (end exclusive). [suggestion] is an optional
 * human-readable quick fix.
 */
data class Diagnostic(
    val severity: Severity,
    val code: DiagnosticCode,
    val message: String,
    val line: Int,
    val startColumn: Int = 0,
    val endColumn: Int = 0,
    val suggestion: String? = null,
)

/** Stable identifiers for diagnostics (useful for tests and filtering). */
enum class DiagnosticCode {
    SYNTAX,
    UNTERMINATED_LITERAL,
    UNKNOWN_DIRECTIVE,
    UNKNOWN_OPCODE,
    OPERAND_COUNT,
    OPERAND_KIND,
    REGISTER_WIDTH,
    REGISTER_OUT_OF_FRAME,
    PARAMETER_OUT_OF_RANGE,
    WIDE_PAIR_OVERFLOW,
    LITERAL_RANGE,
    INVALID_DESCRIPTOR,
    UNDEFINED_LABEL,
    DUPLICATE_LABEL,
    UNUSED_LABEL,
    MISSING_END,
    UNEXPECTED_END,
    MISSING_CLASS,
    MISSING_SUPER,
    DUPLICATE_DIRECTIVE,
    REGISTERS_AND_LOCALS,
    MISSING_REGISTERS,
    RETURN_MISMATCH,
    RANGE_SYNTAX,
    TOO_MANY_REGISTERS,
    ARGUMENT_COUNT,
    MOVE_RESULT_PLACEMENT,
    MOVE_EXCEPTION_PLACEMENT,
    OUTSIDE_METHOD,
    API_LEVEL,
    PAYLOAD_MISMATCH,
    FALLS_OFF_END,
    CODE_IN_ABSTRACT,
    MISSING_CODE,
    DUPLICATE_MEMBER,
}
