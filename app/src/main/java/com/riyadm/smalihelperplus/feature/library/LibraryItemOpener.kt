package com.riyadm.smalihelperplus.feature.library

import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.database.dao.SnippetDao
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.LessonRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute
import com.riyadm.smalihelperplus.navigation.PlaygroundRoute
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a bookmark / history entry ([BookmarkType] + target id) to the route that opens it. Snippets are loaded
 * from Room and handed to the Playground through [CodeHandoff].
 */
@Singleton
class LibraryItemOpener @Inject constructor(
    private val snippetDao: SnippetDao,
    private val codeHandoff: CodeHandoff,
) {
    /** Returns the route to navigate to, or null if the target no longer exists (e.g. a deleted snippet). */
    suspend fun resolve(type: String, targetId: String): AppRoute? = when (type) {
        BookmarkType.OPCODE -> OpcodeDetailRoute(targetId)
        BookmarkType.LESSON -> LessonRoute(targetId)
        BookmarkType.SNIPPET -> {
            val snippet = targetId.toLongOrNull()?.let { snippetDao.getById(it) }
            if (snippet == null) {
                null
            } else {
                codeHandoff.offer(CodeHandoff.PLAYGROUND, snippet.code, snippet.title)
                PlaygroundRoute
            }
        }
        else -> null
    }
}
