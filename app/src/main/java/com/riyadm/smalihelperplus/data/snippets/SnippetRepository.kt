package com.riyadm.smalihelperplus.data.snippets

import com.riyadm.smalihelperplus.core.database.dao.BookmarkDao
import com.riyadm.smalihelperplus.core.database.dao.SnippetDao
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Snippets (built-in and user) plus their bookmarks. Built-in snippets are read-only; user snippets are editable. */
@Singleton
class SnippetRepository @Inject constructor(
    private val snippetDao: SnippetDao,
    private val bookmarkDao: BookmarkDao,
) {
    fun observeSnippets(): Flow<List<SnippetEntity>> = snippetDao.observeAll()

    /** Ids of bookmarked snippets (BookmarkType.SNIPPET, targetId = snippet id). */
    fun observeBookmarkedIds(): Flow<Set<Long>> = bookmarkDao.observeAll().map { list ->
        list.asSequence().filter { it.type == BookmarkType.SNIPPET }.mapNotNull { it.targetId.toLongOrNull() }.toSet()
    }

    suspend fun create(title: String, description: String, collection: String, code: String, tags: List<String>, now: Long): Long =
        snippetDao.insert(
            SnippetEntity(
                title = title.trim(),
                description = description.trim(),
                collection = collection.trim().ifEmpty { SnippetCollections.MY_SNIPPETS },
                code = code,
                tags = tags,
                builtInId = null,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )

    /** Updates a user snippet. Returns false when the snippet does not exist or is built-in. */
    suspend fun update(id: Long, title: String, description: String, collection: String, code: String, tags: List<String>, now: Long): Boolean {
        val existing = snippetDao.getById(id) ?: return false
        if (existing.builtInId != null) return false
        snippetDao.update(
            existing.copy(
                title = title.trim(),
                description = description.trim(),
                collection = collection.trim().ifEmpty { SnippetCollections.MY_SNIPPETS },
                code = code,
                tags = tags,
                updatedAtMillis = now,
            ),
        )
        val bookmarked = bookmarkDao.observeIsBookmarked(BookmarkType.SNIPPET, id.toString()).first()
        if (bookmarked) {
            // Keep the bookmark title in sync.
            bookmarkDao.delete(BookmarkType.SNIPPET, id.toString())
            bookmarkDao.insert(
                BookmarkEntity(type = BookmarkType.SNIPPET, targetId = id.toString(), title = title.trim(), subtitle = collection.trim(), createdAtMillis = now),
            )
        }
        return true
    }

    /** Deletes a user snippet and its bookmark. Built-in snippets cannot be deleted. */
    suspend fun delete(id: Long): Boolean {
        val existing = snippetDao.getById(id) ?: return false
        if (existing.builtInId != null) return false
        snippetDao.delete(id)
        bookmarkDao.delete(BookmarkType.SNIPPET, id.toString())
        return true
    }

    /** Copies any snippet into the user's own snippets. */
    suspend fun duplicate(id: Long, now: Long): Long? {
        val source = snippetDao.getById(id) ?: return null
        return create(
            title = source.title + " (copy)",
            description = source.description,
            collection = SnippetCollections.MY_SNIPPETS,
            code = source.code,
            tags = source.tags,
            now = now,
        )
    }

    suspend fun setBookmarked(snippet: SnippetEntity, bookmarked: Boolean, now: Long) {
        if (bookmarked) {
            bookmarkDao.insert(
                BookmarkEntity(
                    type = BookmarkType.SNIPPET,
                    targetId = snippet.id.toString(),
                    title = snippet.title,
                    subtitle = snippet.collection,
                    createdAtMillis = now,
                ),
            )
        } else {
            bookmarkDao.delete(BookmarkType.SNIPPET, snippet.id.toString())
        }
    }
}
