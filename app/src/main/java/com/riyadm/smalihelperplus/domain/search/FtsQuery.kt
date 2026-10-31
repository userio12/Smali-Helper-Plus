package com.riyadm.smalihelperplus.domain.search

/** Builds safe FTS4 MATCH expressions from free user input. */
object FtsQuery {
    private const val MAX_TOKENS = 8

    /**
     * Turns raw input into an FTS4 expression of AND-ed prefix terms, e.g. `"invoke-static 35c"` becomes
     * `invoke* static* 35c*`. Every character that is not a letter or digit is treated as a separator so the result can
     * never contain FTS operators, quotes, parentheses or column filters. Returns null if nothing searchable remains.
     */
    fun sanitize(raw: String): String? {
        val tokens = raw.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TOKENS)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
