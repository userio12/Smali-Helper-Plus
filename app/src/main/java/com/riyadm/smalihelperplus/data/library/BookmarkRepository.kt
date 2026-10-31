package com.riyadm.smalihelperplus.data.library

import com.riyadm.smalihelperplus.core.database.dao.BookmarkDao
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Bookmarks of opcodes, lessons and snippets ([com.riyadm.smalihelperplus.core.database.entity.BookmarkType]). */
@Singleton
class BookmarkRepository @Inject constructor(private val dao: BookmarkDao) {

    fun observeAll(): Flow<List<BookmarkEntity>> = dao.observeAll()

    fun observeIsBookmarked(type: String, targetId: String): Flow<Boolean> = dao.observeIsBookmarked(type, targetId)

    /**
     * Adds the bookmark if it does not exist, otherwise removes it.
     * @return true if the item is bookmarked after the call.
     */
    suspend fun toggle(type: String, targetId: String, title: String, subtitle: String = ""): Boolean {
        val existing = dao.observeIsBookmarked(type, targetId).first()
        return if (existing) {
            dao.delete(type, targetId)
            false
        } else {
            dao.insert(
                BookmarkEntity(
                    type = type,
                    targetId = targetId,
                    title = title,
                    subtitle = subtitle,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            true
        }
    }

    suspend fun remove(type: String, targetId: String) = dao.delete(type, targetId)

    /** Re-inserts a removed bookmark unchanged (undo); the original id and creation time are kept. */
    suspend fun restore(bookmark: BookmarkEntity) {
        dao.insert(bookmark)
    }
}
