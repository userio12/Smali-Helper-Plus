package com.riyadm.smalihelperplus.domain.search

import com.riyadm.smalihelperplus.domain.model.Opcode

/** Opcode filters shared by the search screen and the opcode list. Empty sets / null / false mean "no restriction". */
data class SearchFilters(
    val categories: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    /** Only instructions available at or below this API level (`opcode.minApi <= maxApi`). */
    val maxApi: Int? = null,
    val hasJavaEquivalent: Boolean = false,
    val wideOnly: Boolean = false,
    val rangeOnly: Boolean = false,
) {
    val isActive: Boolean get() = activeCount > 0

    /** Number of independent restrictions in effect (used for a badge on the filter button). */
    val activeCount: Int
        get() = listOf(
            categories.isNotEmpty(),
            formats.isNotEmpty(),
            maxApi != null,
            hasJavaEquivalent,
            wideOnly,
            rangeOnly,
        ).count { it }

    fun matches(opcode: Opcode): Boolean {
        if (categories.isNotEmpty() && opcode.category !in categories) return false
        if (formats.isNotEmpty() && formats.none { it.equals(opcode.format, ignoreCase = true) }) return false
        if (maxApi != null && opcode.minApi > maxApi) return false
        if (hasJavaEquivalent && opcode.javaEquivalent.isNullOrBlank()) return false
        if (wideOnly && !opcode.isWide) return false
        if (rangeOnly && !opcode.isRange) return false
        return true
    }
}
