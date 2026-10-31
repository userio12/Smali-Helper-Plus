package com.riyadm.smalihelperplus.data.library

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.database.dao.RecentViewDao
import com.riyadm.smalihelperplus.core.database.dao.SearchHistoryDao
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryRepositoryTest {

    private class FakeRecentViewDao : RecentViewDao {
        val rows = MutableStateFlow<List<RecentViewEntity>>(emptyList())
        override fun observeRecent(limit: Int): Flow<List<RecentViewEntity>> =
            rows.map { list -> list.sortedByDescending { it.viewedAtMillis }.take(limit) }

        override suspend fun upsert(item: RecentViewEntity) {
            rows.value = rows.value.filterNot { it.type == item.type && it.targetId == item.targetId } + item
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }

        override suspend fun trim(keep: Int) {
            rows.value = rows.value.sortedByDescending { it.viewedAtMillis }.take(keep)
        }
    }

    private class FakeSearchHistoryDao : SearchHistoryDao {
        val rows = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())
        override fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>> =
            rows.map { list -> list.sortedByDescending { it.lastUsedMillis }.take(limit) }

        override suspend fun get(query: String) = rows.value.firstOrNull { it.query == query }
        override suspend fun upsert(item: SearchHistoryEntity) {
            rows.value = rows.value.filterNot { it.query == item.query } + item
        }

        override suspend fun delete(query: String) {
            rows.value = rows.value.filterNot { it.query == query }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private val views = FakeRecentViewDao()
    private val searches = FakeSearchHistoryDao()
    private val repository = HistoryRepository(views, searches)

    @Test
    fun recordViewUpsertsInsteadOfDuplicating() = runTest {
        repository.recordView("opcode", "move", "move", "Copy")
        repository.recordView("opcode", "move", "move", "Copy")
        repository.recordView("lesson", "move", "Lesson", "")
        assertThat(views.rows.value).hasSize(2)
    }

    @Test
    fun recordViewKeepsOnlyNewest100() = runTest {
        repeat(130) { i ->
            views.upsert(RecentViewEntity("opcode", "op$i", "op$i", "", viewedAtMillis = i.toLong()))
        }
        repository.recordView("opcode", "latest", "latest")
        assertThat(views.rows.value).hasSize(HistoryRepository.MAX_RECENT_VIEWS)
        assertThat(views.rows.value.any { it.targetId == "latest" }).isTrue()
        assertThat(views.rows.value.any { it.targetId == "op0" }).isFalse()
    }

    @Test
    fun recordSearchNormalizesAndCountsUses() = runTest {
        repository.recordSearch("  invoke   static ")
        repository.recordSearch("invoke static")
        repository.recordSearch("a") // too short to remember
        repository.recordSearch("   ")
        val rows = searches.rows.value
        assertThat(rows).hasSize(1)
        assertThat(rows.single().query).isEqualTo("invoke static")
        assertThat(rows.single().useCount).isEqualTo(2)
    }

    @Test
    fun deleteAndClearSearches() = runTest {
        repository.recordSearch("move")
        repository.recordSearch("const")
        repository.deleteSearch("move")
        assertThat(searches.rows.value.map { it.query }).containsExactly("const")
        repository.clearSearches()
        assertThat(searches.rows.value).isEmpty()
        repository.recordView("opcode", "x", "x")
        repository.clearViews()
        assertThat(views.rows.value).isEmpty()
    }

    @Test
    fun normalizeQuery() {
        assertThat(HistoryRepository.normalizeQuery(" a  b ")).isEqualTo("a b")
        assertThat(HistoryRepository.normalizeQuery("x")).isNull()
        assertThat(HistoryRepository.normalizeQuery("x".repeat(200))!!.length).isEqualTo(80)
    }
}
