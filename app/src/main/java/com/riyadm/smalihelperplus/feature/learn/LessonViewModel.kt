package com.riyadm.smalihelperplus.feature.learn

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.content.LessonDto
import com.riyadm.smalihelperplus.data.learning.LearningRepository
import com.riyadm.smalihelperplus.domain.learning.LessonSummary
import com.riyadm.smalihelperplus.navigation.LessonRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data object NotFound : LessonUiState
    data class Content(
        val lesson: LessonDto,
        val summary: LessonSummary?,
        val bookmarked: Boolean,
        val nextLessonId: String?,
        val nextLessonTitle: String?,
    ) : LessonUiState
}

@HiltViewModel
class LessonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LearningRepository,
    private val codeHandoff: CodeHandoff,
) : ViewModel() {

    val lessonId: String = savedStateHandle.toRoute<LessonRoute>().lessonId

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<LessonUiState> = combine(
        repository.lesson(lessonId),
        repository.lessons,
        repository.isBookmarked(lessonId),
    ) { lesson, all, bookmarked ->
        if (lesson == null) {
            LessonUiState.NotFound
        } else {
            val index = all.indexOfFirst { it.id == lessonId }
            val next = if (index >= 0) all.getOrNull(index + 1) else null
            LessonUiState.Content(lesson, all.getOrNull(index), bookmarked, next?.id, next?.title)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonUiState.Loading)

    init {
        viewModelScope.launch { repository.markOpened(lessonId) }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val now = repository.toggleBookmark(lessonId)
            _messages.send(if (now) "Lesson bookmarked" else "Bookmark removed")
        }
    }

    fun markComplete() {
        viewModelScope.launch {
            val unlocked = repository.markCompleted(lessonId)
            _messages.send(
                if (unlocked.isEmpty()) "Lesson completed" else "Lesson completed · Achievement unlocked: ${unlocked.joinToString { it.title }}",
            )
        }
    }

    fun markIncomplete() {
        viewModelScope.launch { repository.markInProgress(lessonId) }
    }

    /** Hands [code] to the Playground; the caller navigates there afterwards. */
    fun sendToPlayground(code: String, title: String) {
        codeHandoff.offer(CodeHandoff.PLAYGROUND, code, title)
    }
}
