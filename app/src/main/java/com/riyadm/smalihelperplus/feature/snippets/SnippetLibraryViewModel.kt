package com.riyadm.smalihelperplus.feature.snippets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.data.snippets.SnippetCollections
import com.riyadm.smalihelperplus.data.snippets.SnippetFilter
import com.riyadm.smalihelperplus.data.snippets.SnippetRepository
import com.riyadm.smalihelperplus.data.snippets.SnippetScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetItem(val snippet: SnippetEntity, val bookmarked: Boolean)

data class SnippetLibraryState(
    val loading: Boolean = true,
    val items: List<SnippetItem> = emptyList(),
    val totalCount: Int = 0,
    /** Collection chips (built-in order first). */
    val collections: List<String> = emptyList(),
    /** All collection names offered when saving a snippet (includes "My snippets"). */
    val collectionChoices: List<String> = listOf(SnippetCollections.MY_SNIPPETS),
    val scope: SnippetScope = SnippetScope.All,
    val query: String = "",
)

@HiltViewModel
class SnippetLibraryViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: SnippetRepository,
    private val handoff: CodeHandoff,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private val scopeKey = savedState.getStateFlow(KEY_SCOPE, SCOPE_ALL)

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<SnippetLibraryState> = combine(
        repository.observeSnippets(),
        repository.observeBookmarkedIds(),
        query,
        scopeKey,
    ) { all, bookmarked, q, key ->
        val scope = decodeScope(key)
        val filtered = SnippetFilter.apply(all, scope, q, bookmarked)
        val names = SnippetCollections.ordered(all.map { it.collection })
        SnippetLibraryState(
            loading = false,
            items = filtered.map { SnippetItem(it, it.id in bookmarked) },
            totalCount = all.size,
            collections = names,
            collectionChoices = (listOf(SnippetCollections.MY_SNIPPETS) + names).distinct(),
            scope = scope,
            query = q,
        )
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SnippetLibraryState())

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
    }

    fun setScope(scope: SnippetScope) {
        savedState[KEY_SCOPE] = encodeScope(scope)
    }

    /** Hands the code to the Playground; the caller then navigates to it. */
    fun offerToPlayground(snippet: SnippetEntity) {
        handoff.offer(CodeHandoff.PLAYGROUND, snippet.code, snippet.title)
    }

    fun toggleBookmark(item: SnippetItem) {
        viewModelScope.launch {
            repository.setBookmarked(item.snippet, !item.bookmarked, System.currentTimeMillis())
            _messages.trySend(if (item.bookmarked) "Bookmark removed" else "Bookmarked")
        }
    }

    fun duplicate(snippet: SnippetEntity) {
        viewModelScope.launch {
            repository.duplicate(snippet.id, System.currentTimeMillis())
            _messages.trySend("Copied to My snippets")
        }
    }

    fun delete(snippet: SnippetEntity) {
        viewModelScope.launch {
            val removed = repository.delete(snippet.id)
            _messages.trySend(if (removed) "Snippet deleted" else "Built-in snippets cannot be deleted")
        }
    }

    /** Creates ([id] == null) or updates a user snippet. */
    fun save(id: Long?, title: String, description: String, collection: String, code: String, tagsText: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val tags = SnippetFilter.parseTags(tagsText)
            if (id == null) {
                repository.create(title, description, collection, code, tags, now)
                _messages.trySend("Snippet saved")
            } else {
                val ok = repository.update(id, title, description, collection, code, tags, now)
                _messages.trySend(if (ok) "Snippet updated" else "That snippet can no longer be edited")
            }
        }
    }

    private fun encodeScope(scope: SnippetScope) = when (scope) {
        SnippetScope.All -> SCOPE_ALL
        SnippetScope.Bookmarked -> SCOPE_BOOKMARKED
        SnippetScope.Mine -> SCOPE_MINE
        is SnippetScope.Collection -> SCOPE_COLLECTION_PREFIX + scope.name
    }

    private fun decodeScope(key: String): SnippetScope = when {
        key == SCOPE_BOOKMARKED -> SnippetScope.Bookmarked
        key == SCOPE_MINE -> SnippetScope.Mine
        key.startsWith(SCOPE_COLLECTION_PREFIX) -> SnippetScope.Collection(key.removePrefix(SCOPE_COLLECTION_PREFIX))
        else -> SnippetScope.All
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_SCOPE = "scope"
        const val SCOPE_ALL = "all"
        const val SCOPE_BOOKMARKED = "bookmarked"
        const val SCOPE_MINE = "mine"
        const val SCOPE_COLLECTION_PREFIX = "c:"
    }
}
