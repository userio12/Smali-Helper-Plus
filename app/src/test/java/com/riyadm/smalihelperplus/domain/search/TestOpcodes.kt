package com.riyadm.smalihelperplus.domain.search

import com.riyadm.smalihelperplus.domain.model.Opcode

/** Small in-memory opcode fixture used by the search tests. */
object TestOpcodes {
    fun op(
        name: String,
        value: Int,
        category: String,
        format: String,
        syntax: String = name,
        summary: String = "",
        description: String = "",
        technical: String = "",
        example: String = "",
        java: String? = null,
        minApi: Int = 1,
        related: List<String> = emptyList(),
        tags: List<String> = emptyList(),
    ) = Opcode(
        name = name, opcode = value, category = category, format = format, syntax = syntax, operands = emptyList(),
        summary = summary, description = description, technical = technical, example = example,
        exampleExplanation = "", javaEquivalent = java, kotlinEquivalent = null, registerUsage = "", minApi = minApi,
        dexVersion = "035", compatibility = "", commonMistakes = emptyList(), related = related,
        references = emptyList(), tags = tags,
    )

    val all: List<Opcode> = listOf(
        op("nop", 0x00, "advanced", "10x", summary = "Does nothing."),
        op("move", 0x01, "move", "12x", "move vA, vB", "Copy a 32-bit value between registers."),
        op("move/from16", 0x02, "move", "22x", "move/from16 vAA, vBBBB", "Copy a value from a wide register number."),
        op("move-wide", 0x04, "move", "12x", "move-wide vA, vB", "Copy a register pair.", tags = listOf("wide")),
        op("move-result", 0x0a, "move", "11x", "move-result vAA", "Store the result of the previous invoke."),
        op("return-void", 0x0e, "return", "10x", "return-void", "Return from a void method."),
        op(
            "const/4", 0x12, "constant", "11n", "const/4 vA, #+B", "Load a 4-bit signed literal.",
            example = "const/4 v0, 0x1\nreturn v0", java = "int v0 = 1;",
        ),
        op("const/16", 0x13, "constant", "21s", "const/16 vAA, #+BBBB", "Load a 16-bit signed literal."),
        op(
            "const-string", 0x1a, "constant", "21c", "const-string vAA, string@BBBB", "Load a string reference.",
            description = "Puts a reference to a string constant into the destination register.",
            example = "const-string v0, \"hello\"",
        ),
        op(
            "iget", 0x52, "field", "22c", "iget vA, vB, field@CCCC", "Read an instance field.",
            minApi = 1, java = "v0 = obj.field;",
        ),
        op("iget-wide", 0x53, "field", "22c", "iget-wide vA, vB, field@CCCC", "Read a long or double instance field."),
        op(
            "invoke-virtual", 0x6e, "invoke", "35c", "invoke-virtual {vC..vG}, meth@BBBB", "Call a virtual method.",
            related = listOf("invoke-static"), java = "obj.method();",
        ),
        op(
            "invoke-static", 0x71, "invoke", "35c", "invoke-static {vC..vG}, meth@BBBB", "Call a static method.",
            description = "Invokes a static method; unlike invoke-virtual there is no receiver.",
            related = listOf("invoke-virtual"), java = "Cls.method();",
        ),
        op(
            "invoke-static/range", 0x77, "invoke", "3rc", "invoke-static/range {vCCCC..vNNNN}, meth@BBBB",
            "Call a static method with a register range.",
        ),
        op("invoke-polymorphic", 0xfa, "method_handle", "45cc", "invoke-polymorphic", "Call a signature polymorphic method.", minApi = 26),
        op("add-int", 0x90, "arithmetic", "23x", "add-int vAA, vBB, vCC", "Add two int registers.", tags = listOf("math")),
        op("add-long", 0x9b, "arithmetic", "23x", "add-long vAA, vBB, vCC", "Add two long values."),
        op(
            "packed-switch-payload", 0x0100, "advanced", "packed-switch-payload", "packed-switch-payload",
            "Jump table data.",
        ),
    )
}
