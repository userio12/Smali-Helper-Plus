package com.riyadm.smalihelperplus.feature.opcodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.data.library.BookmarkRepository
import com.riyadm.smalihelperplus.data.library.HistoryRepository
import com.riyadm.smalihelperplus.data.opcode.OpcodeRepository
import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpcodeDetailUiState(
    val loading: Boolean = true,
    val opcode: Opcode? = null,
    val related: List<Opcode> = emptyList(),
    val bookmarked: Boolean = false,
)

@HiltViewModel
class OpcodeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OpcodeRepository,
    private val bookmarks: BookmarkRepository,
    history: HistoryRepository,
    private val codeHandoff: CodeHandoff,
) : ViewModel() {

    private val requestedName: String = savedStateHandle.toRoute<OpcodeDetailRoute>().name

    val uiState: StateFlow<OpcodeDetailUiState> = repository.observeOpcode(requestedName)
        .flatMapLatest { opcode ->
            if (opcode == null) {
                flowOf(OpcodeDetailUiState(loading = false))
            } else {
                combine(
                    repository.observeRelated(opcode),
                    bookmarks.observeIsBookmarked(BookmarkType.OPCODE, opcode.name),
                ) { related, marked -> OpcodeDetailUiState(false, opcode, related, marked) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpcodeDetailUiState())

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** One-shot snackbar messages. */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    init {
        // Record the view once the opcode is known.
        viewModelScope.launch {
            val opcode = repository.observeOpcode(requestedName).filterNotNull().first()
            history.recordView(BookmarkType.OPCODE, opcode.name, opcode.name, opcode.summary)
        }
    }

    fun toggleBookmark() {
        val opcode = uiState.value.opcode ?: return
        viewModelScope.launch {
            val nowBookmarked = bookmarks.toggle(BookmarkType.OPCODE, opcode.name, opcode.name, opcode.summary)
            messageChannel.send(if (nowBookmarked) "Bookmarked ${opcode.name}" else "Removed bookmark")
        }
    }

    /** Hands the example code to the Playground; the caller then navigates there. */
    fun offerExampleToPlayground() {
        val opcode = uiState.value.opcode ?: return
        if (opcode.example.isBlank()) return
        codeHandoff.offer(CodeHandoff.PLAYGROUND, opcode.example, "${opcode.name} example")
    }

    fun showMessage(text: String) {
        messageChannel.trySend(text)
    }
}
