package com.riyadm.smalihelperplus.data.backup

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import org.junit.Test

class SnippetBackupReferencesTest {
    private fun snippet(id: Long, builtInId: String? = null, code: String = "code") =
        SnippetEntity(id = id, title = "Title", code = code, builtInId = builtInId, createdAtMillis = 1, updatedAtMillis = 1)
    private fun data(id: String) = BackupData(
        bookmarks = listOf(BookmarkBackup("snippet", id, "Title", createdAtMillis = 1)),
        recentViews = listOf(RecentViewBackup("snippet", id, "Title", viewedAtMillis = 1)),
    )

    @Test fun userReferencesSurviveDifferentDeviceIds() {
        val exported = portableSnippetReferences(data("42"), listOf(snippet(42)))
        val restored = resolveSnippetReferences(exported, listOf(snippet(99)))
        assertThat(restored.bookmarks.single().targetId).isEqualTo("99")
        assertThat(restored.recentViews.single().targetId).isEqualTo("99")
    }

    @Test fun builtInIdentitySurvivesContentChanges() {
        val exported = portableSnippetReferences(data("42"), listOf(snippet(42, "builtin")))
        val restored = resolveSnippetReferences(exported, listOf(snippet(7, "builtin", "updated")))
        assertThat(restored.bookmarks.single().targetId).isEqualTo("7")
    }

    @Test fun numericLegacyIdsNeverBindToUnrelatedLocalRows() {
        assertThat(resolveSnippetReferences(data("42"), listOf(snippet(42))).bookmarks).isEmpty()
    }

    @Test fun unresolvedBookmarksAreOmittedAndOrdinaryBookmarksArePreserved() {
        val ordinary = BookmarkBackup("opcode", "move", "move", createdAtMillis = 1)
        val restored = resolveSnippetReferences(data("user:missing").copy(bookmarks = data("user:missing").bookmarks + ordinary), emptyList())
        assertThat(restored.bookmarks).containsExactly(ordinary)
    }
}
