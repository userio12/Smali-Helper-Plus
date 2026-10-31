package com.riyadm.smalihelperplus.data.library

import com.riyadm.smalihelperplus.core.database.dao.RecentViewDao
import com.riyadm.smalihelperplus.core.database.dao.SearchHistoryDao
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Recently viewed items (opcodes, lessons, snippets) and recent search queries. */
@Singleton
class HistoryRepository @Inject constructor(
    private val recentViews: RecentViewDao,
    private val searches: SearchHistoryDao,
) {
    // ---- recently viewed ----

    fun observeRecentViews(limit: Int = MAX_RECENT_VIEWS): Flow<List<RecentViewEntity>> = recentViews.observeRecent(limit)

    /** Records (or refreshes the timestamp of) a view and keeps only the newest [MAX_RECENT_VIEWS]. */
    suspend fun recordView(type: String, targetId: String, title: String, subtitle: String = "") {
        recentViews.upsert(
            RecentViewEntity(
                type = type,
                targetId = targetId,
                title = title,
                subtitle = subtitle,
                viewedAtMillis = System.currentTimeMillis(),
            ),
        )
        recentViews.trim(MAX_RECENT_VIEWS)
    }

    suspend fun clearViews() = recentViews.clear()

    // ---- search history ----

    fun observeSearches(limit: Int = MAX_SEARCHES): Flow<List<SearchHistoryEntity>> = searches.observeRecent(limit)

    /** Stores a submitted query (trimmed); repeated queries move to the top and increase their use count. */
    suspend fun recordSearch(query: String) {
        val normalized = normalizeQuery(query) ?: return
        val existing = searches.get(normalized)
        searches.upsert(
            SearchHistoryEntity(
                query = normalized,
                lastUsedMillis = System.currentTimeMillis(),
                useCount = (existing?.useCount ?: 0) + 1,
            ),
        )
    }

    suspend fun deleteSearch(query: String) = searches.delete(query)

    suspend fun clearSearches() = searches.clear()

    companion object {
        const val MAX_RECENT_VIEWS = 100
        const val MAX_SEARCHES = 20

        /** Trims and collapses whitespace; returns null for queries not worth remembering (blank or one character). */
        fun normalizeQuery(query: String): String? =
            query.trim().replace(Regex("\\s+"), " ").takeIf { it.length >= 2 }?.take(80)
    }
}
