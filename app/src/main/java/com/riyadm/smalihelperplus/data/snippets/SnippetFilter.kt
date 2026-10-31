package com.riyadm.smalihelperplus.data.snippets

import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity

/** Which subset of the library is shown. */
sealed interface SnippetScope {
    data object All : SnippetScope
    data object Bookmarked : SnippetScope

    /** Snippets created by the user (including duplicates of built-ins). */
    data object Mine : SnippetScope
    data class Collection(val name: String) : SnippetScope
}

object SnippetCollections {
    const val MY_SNIPPETS = "My snippets"

    /** Built-in collection names in display order. */
    val BUILT_IN = listOf("Classes", "Methods", "Strings", "Arrays", "Conditions", "Loops", "Method calls", "Exceptions")

    /** Orders collection names: built-ins first (fixed order), then the rest alphabetically. */
    fun ordered(names: Collection<String>): List<String> {
        val distinct = names.filter { it.isNotBlank() }.distinct()
        val known = BUILT_IN.filter { it in distinct }
        val others = distinct.filter { it !in BUILT_IN && it != MY_SNIPPETS }.sortedBy { it.lowercase() }
        return known + others
    }
}

object SnippetFilter {
    fun isUserSnippet(s: SnippetEntity) = s.builtInId == null

    /**
     * Applies scope and free text search. Every word of [query] must occur in the title, description, collection,
     * tags or code (case-insensitive). Result order: collection order, then database order for built-ins and most
     * recently changed first for user snippets.
     */
    fun apply(
        all: List<SnippetEntity>,
        scope: SnippetScope,
        query: String,
        bookmarkedIds: Set<Long>,
    ): List<SnippetEntity> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val scoped = all.filter { s ->
            when (scope) {
                SnippetScope.All -> true
                SnippetScope.Bookmarked -> s.id in bookmarkedIds
                SnippetScope.Mine -> isUserSnippet(s)
                is SnippetScope.Collection -> s.collection == scope.name
            }
        }
        val matched = if (words.isEmpty()) scoped else scoped.filter { s ->
            val haystack = (listOf(s.title, s.description, s.collection, s.code) + s.tags).joinToString("\n").lowercase()
            words.all { it in haystack }
        }
        val order = SnippetCollections.ordered(all.map { it.collection })
        return matched.sortedWith(
            compareBy<SnippetEntity>(
                { if (isUserSnippet(it)) 0 else 1 },
                { order.indexOf(it.collection).let { i -> if (i == -1) Int.MAX_VALUE else i } },
            ).thenByDescending { if (isUserSnippet(it)) it.updatedAtMillis else 0L }
                .thenBy { it.id },
        )
    }

    /** Parses a comma / newline separated tag string into a clean tag list. */
    fun parseTags(text: String): List<String> =
        text.split(',', '\n').map { it.trim().lowercase().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().take(12)
}
