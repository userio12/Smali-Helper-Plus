package com.riyadm.smalihelperplus.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.SearchHistoryEntity
import com.riyadm.smalihelperplus.data.library.BookmarkRepository
import com.riyadm.smalihelperplus.data.library.HistoryRepository
import com.riyadm.smalihelperplus.data.opcode.OpcodeRepository
import com.riyadm.smalihelperplus.domain.search.OpcodeSearchEngine
import com.riyadm.smalihelperplus.domain.search.SearchFilters
import com.riyadm.smalihelperplus.domain.search.SearchResult
import com.riyadm.smalihelperplus.navigation.SearchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result set for one (query, filters) pair. */
data class SearchOutput(
    val query: String,
    val results: List<SearchResult>,
    val suggestions: List<String>,
    val didYouMean: String?,
)

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    /** False until the opcode index is loaded. */
    val ready: Boolean = false,
    /** True while the typed query has not been searched yet (debounce window). */
    val pending: Boolean = false,
    val output: SearchOutput? = null,
    val formats: List<String> = emptyList(),
    val apiLevels: List<Int> = emptyList(),
    val totalOpcodes: Int = 0,
    val bookmarked: Set<String> = emptySet(),
) {
    /** True when there is something to search for (text and/or filters). */
    val isSearching: Boolean get() = query.isNotBlank() || filters.isActive
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: OpcodeRepository,
    private val history: HistoryRepository,
    bookmarks: BookmarkRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val query = MutableStateFlow(savedStateHandle.get<String>("search_query") ?: savedStateHandle.toRoute<SearchRoute>().query)
    private val filters = MutableStateFlow(SearchFilters())

    private val engine: Flow<OpcodeSearchEngine> = repository.searchEngine.filterNotNull()

    /** Debounced (120 ms) queries; clearing the field is applied immediately. */
    private val debouncedQuery: Flow<String> = query.debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }

    private val output: Flow<SearchOutput> = combine(debouncedQuery, filters, engine) { q, f, e -> Triple(q, f, e) }
        .mapLatest { (q, f, e) ->
            val fts = if (q.isBlank()) emptySet() else repository.ftsMatchedNames(q)
            val results = e.search(q, f, fts)
            SearchOutput(
                query = q,
                results = results,
                suggestions = if (q.isBlank()) emptyList() else e.suggestions(q),
                didYouMean = if (q.isNotBlank() && results.isEmpty()) e.didYouMean(q) else null,
            )
        }
        .flowOn(defaultDispatcher)

    private val bookmarkedNames: Flow<Set<String>> = bookmarks.observeAll().map { list ->
        list.filter { it.type == BookmarkType.OPCODE }.mapTo(HashSet()) { it.targetId }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        filters,
        output.onStart<SearchOutput?> { emit(null) },
        engine.onStart<OpcodeSearchEngine?> { emit(null) },
        bookmarkedNames,
    ) { q, f, out, e, marked ->
        SearchUiState(
            query = q,
            filters = f,
            ready = e != null,
            pending = out == null || out.query != q,
            output = out,
            formats = e?.formats.orEmpty(),
            apiLevels = e?.apiLevels.orEmpty(),
            totalOpcodes = e?.size ?: 0,
            bookmarked = marked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(query = query.value))

    val recentSearches: StateFlow<List<SearchHistoryEntity>> = history.observeSearches(RECENT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The query as currently held by the view model (initial value of the text field). */
    val currentQuery: String get() = query.value

    fun setQuery(text: String) {
        savedStateHandle["search_query"] = text
        query.value = text
    }

    fun setFilters(transform: (SearchFilters) -> SearchFilters) = filters.update(transform)

    fun resetFilters() = filters.update { SearchFilters() }

    /** Remembers the current query (called when the user submits it or opens a result). */
    fun saveQuery() {
        val text = query.value
        viewModelScope.launch { history.recordSearch(text) }
    }

    fun deleteRecent(text: String) {
        viewModelScope.launch { history.deleteSearch(text) }
    }

    fun clearRecent() {
        viewModelScope.launch { history.clearSearches() }
    }

    private companion object {
        const val DEBOUNCE_MS = 120L
        const val RECENT_LIMIT = 12
    }
}
