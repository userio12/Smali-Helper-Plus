package com.riyadm.smalihelperplus.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.data.library.BookmarkRepository
import com.riyadm.smalihelperplus.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Filter tabs of the bookmarks list. [type] is null for "All". */
enum class BookmarkFilter(val label: String, val type: String?) {
    ALL("All", null),
    OPCODES("Opcodes", BookmarkType.OPCODE),
    LESSONS("Lessons", BookmarkType.LESSON),
    SNIPPETS("Snippets", BookmarkType.SNIPPET),
}

data class BookmarksUiState(
    val loading: Boolean = true,
    val filter: BookmarkFilter = BookmarkFilter.ALL,
    val items: List<BookmarkEntity> = emptyList(),
    val totalCount: Int = 0,
    val counts: Map<BookmarkFilter, Int> = emptyMap(),
)

sealed interface BookmarksEvent {
    data class Removed(val bookmark: BookmarkEntity) : BookmarksEvent
    data class Message(val text: String) : BookmarksEvent
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
    private val opener: LibraryItemOpener,
) : ViewModel() {

    private val filter = MutableStateFlow(BookmarkFilter.ALL)

    val uiState: StateFlow<BookmarksUiState> = combine(repository.observeAll(), filter) { all, selected ->
        BookmarksUiState(
            loading = false,
            filter = selected,
            items = if (selected.type == null) all else all.filter { it.type == selected.type },
            totalCount = all.size,
            counts = BookmarkFilter.entries.associateWith { f -> if (f.type == null) all.size else all.count { it.type == f.type } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    private val eventChannel = Channel<BookmarksEvent>(Channel.BUFFERED)
    val events: Flow<BookmarksEvent> = eventChannel.receiveAsFlow()

    fun setFilter(value: BookmarkFilter) = filter.update { value }

    fun remove(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.remove(bookmark.type, bookmark.targetId)
            eventChannel.send(BookmarksEvent.Removed(bookmark))
        }
    }

    fun undoRemove(bookmark: BookmarkEntity) {
        viewModelScope.launch { repository.restore(bookmark) }
    }

    /** Opens a bookmark; [navigate] is called with the destination, or a message is shown if it no longer exists. */
    fun open(bookmark: BookmarkEntity, navigate: (AppRoute) -> Unit) {
        viewModelScope.launch {
            val route = opener.resolve(bookmark.type, bookmark.targetId)
            if (route != null) {
                navigate(route)
            } else {
                eventChannel.send(BookmarksEvent.Message("\"${bookmark.title}\" no longer exists"))
            }
        }
    }
}
