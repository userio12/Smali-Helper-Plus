package com.riyadm.smalihelperplus.domain.tools

/** Matching used by the Tools hub search box: every word of the query must appear in the title, text or keywords. */
object ToolSearch {
    fun matches(query: String, title: String, description: String, keywords: List<String> = emptyList()): Boolean {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = (listOf(title, description) + keywords).joinToString(" ").lowercase()
        return words.all { it in haystack }
    }
}
