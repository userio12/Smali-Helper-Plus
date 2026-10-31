package com.riyadm.smalihelperplus.domain.model

import com.riyadm.smalihelperplus.core.content.OperandDto
import com.riyadm.smalihelperplus.core.content.ReferenceDto

/** Domain model of one Dalvik instruction (decoded from the Room [com.riyadm.smalihelperplus.core.database.entity.OpcodeEntity]). */
data class Opcode(
    val name: String,
    val opcode: Int,
    /** [OpcodeCategory] id. */
    val category: String,
    val format: String,
    val syntax: String,
    val operands: List<OperandDto>,
    val summary: String,
    val description: String,
    val technical: String,
    val example: String,
    val exampleExplanation: String,
    val javaEquivalent: String?,
    val kotlinEquivalent: String?,
    val registerUsage: String,
    val minApi: Int,
    val dexVersion: String,
    val compatibility: String,
    val commonMistakes: List<String>,
    val related: List<String>,
    val references: List<ReferenceDto>,
    val tags: List<String>,
) {
    val categoryOrNull: OpcodeCategory? get() = OpcodeCategory.fromId(category)

    val categoryTitle: String get() = categoryOrNull?.title ?: category

    /** "0x12" for ordinary opcodes, "0x0100" for the payload pseudo-instruction idents. */
    val hexValue: String get() = if (opcode > 0xFF) "0x%04X".format(opcode) else "0x%02X".format(opcode)

    /** Name segments split on '-', '/' and '_' (e.g. "invoke-static/range" -> invoke, static, range). */
    val nameTokens: List<String> get() = name.lowercase().split('-', '/', '_').filter { it.isNotEmpty() }

    /** Operates on 64-bit values held in register pairs (wide / long / double instructions). */
    val isWide: Boolean
        get() = nameTokens.any { it == "wide" || it == "long" || it == "double" } || tags.any { it.equals("wide", true) }

    /** Takes a register range ("/range" instructions, formats 3rc and 4rcc). */
    val isRange: Boolean
        get() = name.endsWith("/range") || format.equals("3rc", true) || format.equals("4rcc", true) ||
            tags.any { it.equals("range", true) }
}
