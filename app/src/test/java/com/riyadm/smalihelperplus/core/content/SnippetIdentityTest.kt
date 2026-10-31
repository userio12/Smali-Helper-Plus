package com.riyadm.smalihelperplus.core.content

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import org.junit.Test

class SnippetIdentityTest {
    private fun snippet(id: Long, builtIn: String?, code: String = "old") =
        SnippetEntity(id = id, title = "Title", code = code, builtInId = builtIn, createdAtMillis = 12, updatedAtMillis = 0)

    @Test fun reinstallPreservesBookmarkTargetAndCreationDate() {
        val result = preserveBuiltInSnippetIds(listOf(snippet(42, "example")), listOf(snippet(0, "example", "updated")))
        assertThat(result.single().id).isEqualTo(42)
        assertThat(result.single().createdAtMillis).isEqualTo(12)
        assertThat(result.single().code).isEqualTo("updated")
    }

    @Test fun newBuiltInCannotReuseUserOrUnrelatedId() {
        val result = preserveBuiltInSnippetIds(listOf(snippet(42, null), snippet(43, "old")), listOf(snippet(42, "new")))
        assertThat(result.single().id).isEqualTo(0)
    }
}
