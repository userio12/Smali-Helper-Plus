package com.riyadm.smalihelperplus.feature.opcodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.data.library.BookmarkRepository
import com.riyadm.smalihelperplus.data.opcode.OpcodeRepository
import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.domain.model.OpcodeCategory
import com.riyadm.smalihelperplus.domain.search.OpcodeSearchEngine
import com.riyadm.smalihelperplus.domain.search.SearchFilters
import com.riyadm.smalihelperplus.domain.search.SearchResult
import com.riyadm.smalihelperplus.navigation.OpcodeListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class OpcodeSort(val label: String) {
    VALUE("Opcode value"),
    NAME("Name (A-Z)"),
}

data class OpcodeListUiState(
    val loading: Boolean = true,
    val title: String = "Opcodes",
    val category: OpcodeCategory? = null,
    val items: List<SearchResult> = emptyList(),
    /** Number of opcodes in this list before any filtering. */
    val totalInScope: Int = 0,
    val filterText: String = "",
    val sort: OpcodeSort = OpcodeSort.VALUE,
    val filters: SearchFilters = SearchFilters(),
    val availableFormats: List<String> = emptyList(),
    val availableApiLevels: List<Int> = emptyList(),
    val bookmarked: Set<String> = emptySet(),
)

@HiltViewModel
class OpcodeListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: OpcodeRepository,
    bookmarks: BookmarkRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val categoryId: String? = savedStateHandle.toRoute<OpcodeListRoute>().category
    private val category: OpcodeCategory? = categoryId?.let { OpcodeCategory.fromId(it) }
    private val title: String = when {
        categoryId == null -> "All instructions"
        else -> category?.title ?: categoryId
    }

    private val filterText = MutableStateFlow("")
    private val sort = MutableStateFlow(OpcodeSort.VALUE)
    private val filters = MutableStateFlow(SearchFilters())

    private data class Scope(val opcodes: List<Opcode>, val engine: OpcodeSearchEngine)

    private val scope = repository.observeOpcodes(categoryId).map { Scope(it, OpcodeSearchEngine(it)) }

    private val bookmarkedNames = bookmarks.observeAll().map { list ->
        list.filter { it.type == BookmarkType.OPCODE }.mapTo(HashSet()) { it.targetId } as Set<String>
    }

    val uiState: StateFlow<OpcodeListUiState> = combine(scope, filterText, sort, filters, bookmarkedNames) { s, text, order, f, marked ->
        val items = if (text.isBlank()) {
            val visible = s.opcodes.filter { f.matches(it) }
            val ordered = when (order) {
                OpcodeSort.VALUE -> visible
                OpcodeSort.NAME -> visible.sortedBy { it.name }
            }
            ordered.map { it.asListResult() }
        } else {
            s.engine.search(text, f)
        }
        OpcodeListUiState(
            loading = false,
            title = title,
            category = category,
            items = items,
            totalInScope = s.opcodes.size,
            filterText = text,
            sort = order,
            filters = f,
            availableFormats = s.engine.formats,
            availableApiLevels = s.engine.apiLevels,
            bookmarked = marked,
        )
    }.flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpcodeListUiState(title = title, category = category))

    fun setFilterText(text: String) = filterText.update { text }

    fun setSort(value: OpcodeSort) = sort.update { value }

    fun updateFilters(transform: (SearchFilters) -> SearchFilters) = filters.update(transform)

    fun clearFilters() {
        filters.update { SearchFilters() }
        filterText.update { "" }
    }
}
