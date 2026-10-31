package com.riyadm.smalihelperplus.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.content.BundledContentSeeder
import com.riyadm.smalihelperplus.data.learning.LearningRepository
import com.riyadm.smalihelperplus.domain.learning.LearningSummary
import com.riyadm.smalihelperplus.domain.learning.LessonLevel
import com.riyadm.smalihelperplus.domain.learning.LessonSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface LearnUiState {
    data object Loading : LearnUiState
    /** Content seeding finished but no lessons are installed. */
    data object Empty : LearnUiState
    data class Content(
        val summary: LearningSummary,
        val levels: List<Pair<LessonLevel, List<LessonSummary>>>,
    ) : LearnUiState
}

@HiltViewModel
class LearnViewModel @Inject constructor(
    repository: LearningRepository,
    seeder: BundledContentSeeder,
) : ViewModel() {

    val state: StateFlow<LearnUiState> = combine(seeder.ready, repository.lessons, repository.summary) { ready, lessons, summary ->
        when {
            lessons.isNotEmpty() -> LearnUiState.Content(
                summary = summary,
                levels = LessonLevel.entries.map { level -> level to lessons.filter { it.level == level }.sortedBy { it.order } }
                    .filter { it.second.isNotEmpty() },
            )
            ready -> LearnUiState.Empty
            else -> LearnUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnUiState.Loading)
}
