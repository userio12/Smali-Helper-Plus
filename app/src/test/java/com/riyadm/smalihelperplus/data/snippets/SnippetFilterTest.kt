package com.riyadm.smalihelperplus.data.snippets

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import org.junit.Test

class SnippetFilterTest {

    private fun snippet(
        id: Long,
        title: String,
        collection: String,
        code: String = ".method m()V\n.end method",
        tags: List<String> = emptyList(),
        builtIn: String? = "b$id",
        updated: Long = 0,
        description: String = "",
    ) = SnippetEntity(
        id = id, title = title, description = description, collection = collection, code = code, tags = tags,
        builtInId = builtIn, createdAtMillis = 0, updatedAtMillis = updated,
    )

    private val all = listOf(
        snippet(1, "Counting loop", "Loops", tags = listOf("for", "counter")),
        snippet(2, "String equals", "Strings", code = "invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z"),
        snippet(3, "Minimal class", "Classes", description = "The smallest useful class"),
        snippet(4, "My helper", "My snippets", builtIn = null, updated = 50),
        snippet(5, "Newer helper", "Custom stuff", builtIn = null, updated = 100),
    )

    @Test fun allScopeKeepsEveryoneUserSnippetsFirstThenCollectionOrder() {
        val result = SnippetFilter.apply(all, SnippetScope.All, "", emptySet())
        assertThat(result.map { it.id }).containsExactly(5L, 4L, 3L, 2L, 1L).inOrder()
    }

    @Test fun collectionScope() {
        val result = SnippetFilter.apply(all, SnippetScope.Collection("Strings"), "", emptySet())
        assertThat(result.map { it.id }).containsExactly(2L)
    }

    @Test fun mineScopeIncludesCustomCollections() {
        val result = SnippetFilter.apply(all, SnippetScope.Mine, "", emptySet())
        assertThat(result.map { it.id }).containsExactly(5L, 4L).inOrder()
    }

    @Test fun bookmarkedScope() {
        val result = SnippetFilter.apply(all, SnippetScope.Bookmarked, "", setOf(1L, 3L, 99L))
        assertThat(result.map { it.id }).containsExactly(3L, 1L).inOrder()
    }

    @Test fun searchMatchesTitleTagsDescriptionAndCode() {
        assertThat(SnippetFilter.apply(all, SnippetScope.All, "counter", emptySet()).map { it.id }).containsExactly(1L)
        assertThat(SnippetFilter.apply(all, SnippetScope.All, "smallest", emptySet()).map { it.id }).containsExactly(3L)
        assertThat(SnippetFilter.apply(all, SnippetScope.All, "invoke-virtual", emptySet()).map { it.id }).containsExactly(2L)
        assertThat(SnippetFilter.apply(all, SnippetScope.All, "EQUALS string", emptySet()).map { it.id }).containsExactly(2L)
        assertThat(SnippetFilter.apply(all, SnippetScope.All, "nothing-like-this", emptySet())).isEmpty()
    }

    @Test fun searchAndScopeCombine() {
        val result = SnippetFilter.apply(all, SnippetScope.Collection("Loops"), "string", emptySet())
        assertThat(result).isEmpty()
    }

    @Test fun collectionOrdering() {
        val ordered = SnippetCollections.ordered(listOf("Zeta", "Strings", "Classes", "My snippets", "alpha", "Loops", "Strings"))
        assertThat(ordered).containsExactly("Classes", "Strings", "Loops", "alpha", "Zeta").inOrder()
    }

    @Test fun parseTags() {
        assertThat(SnippetFilter.parseTags("Loop, #counter,, loop\nfast ")).containsExactly("loop", "counter", "fast").inOrder()
        assertThat(SnippetFilter.parseTags("")).isEmpty()
        assertThat(SnippetFilter.parseTags((1..20).joinToString(",") { "t$it" })).hasSize(12)
    }

    @Test fun userSnippetDetection() {
        assertThat(SnippetFilter.isUserSnippet(all[3])).isTrue()
        assertThat(SnippetFilter.isUserSnippet(all[0])).isFalse()
    }
}
