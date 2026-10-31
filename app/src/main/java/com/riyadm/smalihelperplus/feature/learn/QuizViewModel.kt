package com.riyadm.smalihelperplus.feature.learn

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.riyadm.smalihelperplus.data.learning.LearningRepository
import com.riyadm.smalihelperplus.domain.learning.QuizItem
import com.riyadm.smalihelperplus.domain.learning.QuizOutcome
import com.riyadm.smalihelperplus.navigation.QuizRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QuizMode { LESSON, DAILY, MIXED }

data class QuizUiState(
    val loading: Boolean = true,
    val mode: QuizMode = QuizMode.LESSON,
    val title: String = "",
    val questions: List<QuizItem> = emptyList(),
    val index: Int = 0,
    /** Chosen option per question (null = not answered yet). */
    val answers: List<Int?> = emptyList(),
    val finished: Boolean = false,
    val outcome: QuizOutcome? = null,
    val saveFailed: Boolean = false,
    val nextLessonId: String? = null,
    val nextLessonTitle: String? = null,
) {
    val current: QuizItem? get() = questions.getOrNull(index)
    val selected: Int? get() = answers.getOrNull(index)
    val answered: Boolean get() = selected != null
    val isLast: Boolean get() = index == questions.lastIndex
    val correctCount: Int get() = questions.indices.count { i -> answers.getOrNull(i) == questions[i].question.correctIndex }
}

@HiltViewModel
class QuizViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LearningRepository,
) : ViewModel() {

    val quizId: String = savedStateHandle.toRoute<QuizRoute>().lessonId
    private val mode = when (quizId) {
        QuizRoute.DAILY -> QuizMode.DAILY
        QuizRoute.MIXED -> QuizMode.MIXED
        else -> QuizMode.LESSON
    }

    private val _state = MutableStateFlow(QuizUiState(mode = mode))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val questions = when (mode) {
                QuizMode.DAILY -> repository.dailyPractice()
                QuizMode.MIXED -> repository.mixedQuiz()
                QuizMode.LESSON -> repository.quizForLesson(quizId)
            }
            val title = when (mode) {
                QuizMode.DAILY -> "Daily practice"
                QuizMode.MIXED -> "Mixed quiz"
                QuizMode.LESSON -> questions.firstOrNull()?.lessonTitle ?: repository.getLesson(quizId)?.title ?: "Quiz"
            }
            val next = if (mode == QuizMode.LESSON) repository.nextLessonId(quizId) else null
            val nextTitle = next?.let { repository.getLesson(it)?.title }
            _state.value = QuizUiState(
                loading = false,
                mode = mode,
                title = title,
                questions = questions,
                answers = List(questions.size) { null },
                nextLessonId = next,
                nextLessonTitle = nextTitle,
            )
        }
    }

    fun select(option: Int) {
        _state.update { s ->
            val q = s.current ?: return@update s
            if (s.answered || s.finished || option !in q.question.options.indices) return@update s
            s.copy(answers = s.answers.toMutableList().also { it[s.index] = option })
        }
    }

    fun next() {
        val s = _state.value
        if (!s.answered || s.finished) return
        if (s.isLast) finish() else _state.update { it.copy(index = it.index + 1) }
    }

    private fun finish() {
        val s = _state.value
        _state.update { it.copy(finished = true) }
        viewModelScope.launch {
            val outcome = runCatching {
                repository.recordQuizResult(
                    lessonId = when (mode) {
                        QuizMode.DAILY -> LearningRepository.PRACTICE_DAILY
                        QuizMode.MIXED -> LearningRepository.PRACTICE_MIXED
                        QuizMode.LESSON -> quizId
                    },
                    correct = s.correctCount,
                    total = s.questions.size,
                )
            }
            _state.update {
                it.copy(
                    outcome = outcome.getOrNull() ?: QuizOutcome(s.correctCount, s.questions.size, false, emptyList()),
                    saveFailed = outcome.isFailure,
                )
            }
        }
    }

    /** Starts over. Lesson and daily quizzes repeat the same questions; a mixed quiz draws a new set. */
    fun retry() {
        if (mode == QuizMode.MIXED) {
            load()
        } else {
            _state.update {
                it.copy(index = 0, answers = List(it.questions.size) { null }, finished = false, outcome = null, saveFailed = false)
            }
        }
    }
}
