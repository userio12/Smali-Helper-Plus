package com.riyadm.smalihelperplus.data.backup

import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import java.security.MessageDigest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** User identity matches BackupMerger's deduplication key and never exposes a device-local row id. */
internal fun snippetBackupKey(snippet: SnippetEntity): String = snippet.builtInId?.let { "builtin:$it" } ?: run {
    val identity = BackupValidator.writeJson.encodeToString(
        ListSerializer(String.serializer()), listOf(snippet.title, snippet.collection, snippet.code),
    )
    "user:" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal fun portableSnippetReferences(data: BackupData, snippets: List<SnippetEntity>): BackupData =
    remapSnippetReferences(data, snippets.associate { it.id.toString() to snippetBackupKey(it) })

internal fun resolveSnippetReferences(data: BackupData, snippets: List<SnippetEntity>): BackupData =
    remapSnippetReferences(data, snippets.associate { snippetBackupKey(it) to it.id.toString() })

private fun remapSnippetReferences(data: BackupData, targets: Map<String, String>): BackupData = data.copy(
    bookmarks = data.bookmarks.mapNotNull { row ->
        if (row.type != BookmarkType.SNIPPET) row else targets[row.targetId]?.let { row.copy(targetId = it) }
    },
    recentViews = data.recentViews.mapNotNull { row ->
        if (row.type != BookmarkType.SNIPPET) row else targets[row.targetId]?.let { row.copy(targetId = it) }
    },
)
// Version 1 had no portable snippet identity. Its snippet references are deliberately omitted on import:
// treating an old numeric id as a local id can silently bookmark an unrelated snippet.
