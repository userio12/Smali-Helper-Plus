package com.riyadm.smalihelperplus.feature.opcodes

import com.riyadm.smalihelperplus.domain.model.Opcode

/** Plain-text renderings of an opcode for copy and share. */
object OpcodeText {

    /** Formatted summary used by the Copy and Share actions. */
    fun summary(opcode: Opcode): String = buildString {
        appendLine("${opcode.name} (${opcode.hexValue}) - ${opcode.categoryTitle}")
        appendLine("Format ${opcode.format} | API ${opcode.minApi}+ | DEX ${opcode.dexVersion}")
        appendLine()
        appendLine("Syntax:")
        appendLine(opcode.syntax)
        appendLine()
        appendLine(opcode.summary)
        if (opcode.description.isNotBlank()) {
            appendLine()
            appendLine(opcode.description.trim())
        }
        if (opcode.operands.isNotEmpty()) {
            appendLine()
            appendLine("Operands:")
            opcode.operands.forEach { appendLine("  ${it.name}: ${it.description}") }
        }
        if (opcode.example.isNotBlank()) {
            appendLine()
            appendLine("Example:")
            appendLine(opcode.example.trimEnd())
            if (opcode.exampleExplanation.isNotBlank()) appendLine(opcode.exampleExplanation.trim())
        }
        opcode.javaEquivalent?.let {
            appendLine()
            appendLine("Java equivalent (educational pseudocode):")
            appendLine(it.trimEnd())
        }
        if (opcode.commonMistakes.isNotEmpty()) {
            appendLine()
            appendLine("Common mistakes:")
            opcode.commonMistakes.forEach { appendLine("  - $it") }
        }
        appendLine()
        append("Smali Helper Plus")
    }.trimEnd()

    /** True for web links that are safe to hand to a browser. */
    fun isOpenableUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://")
    }
}
