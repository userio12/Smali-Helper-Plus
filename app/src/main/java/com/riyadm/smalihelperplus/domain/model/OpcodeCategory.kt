package com.riyadm.smalihelperplus.domain.model

/** Opcode categories. [id] values are the canonical strings stored in content JSON and Room. */
enum class OpcodeCategory(val id: String, val title: String, val blurb: String) {
    MOVE("move", "Move", "Copy values and results between registers"),
    RETURN("return", "Return", "Leave a method, optionally with a value"),
    CONSTANT("constant", "Constants", "Load literals, strings, classes and handles"),
    MONITOR("monitor", "Monitors", "Enter and exit object locks"),
    TYPE("type", "Type operations", "Casts, instanceof and type checks"),
    OBJECT("object", "Objects", "Create instances"),
    ARRAY("array", "Arrays", "Create, fill, read and write arrays"),
    FIELD("field", "Fields", "Read and write instance and static fields"),
    INVOKE("invoke", "Method invocation", "Call virtual, direct, static and interface methods"),
    ARITHMETIC("arithmetic", "Arithmetic", "Add, subtract, multiply, divide and negate"),
    BITWISE("bitwise", "Bitwise", "AND, OR, XOR, NOT and shifts"),
    COMPARISON("comparison", "Comparison", "Compare long, float and double values"),
    BRANCH("branch", "Conditional branches", "if-* tests against registers or zero"),
    JUMP("jump", "Jumps", "Unconditional goto"),
    SWITCH("switch", "Switches", "Packed and sparse switch tables"),
    CONVERSION("conversion", "Type conversions", "Widen, narrow and convert numeric types"),
    EXCEPTION("exception", "Exception handling", "Throw and catch exceptions"),
    METHOD_HANDLE("method_handle", "Method handles", "invoke-polymorphic, invoke-custom and handle constants"),
    ADVANCED("advanced", "Advanced", "nop and data payload pseudo-instructions"),
    ;

    companion object {
        fun fromId(id: String): OpcodeCategory? = entries.firstOrNull { it.id == id }
    }
}
