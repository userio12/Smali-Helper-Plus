package com.riyadm.smalihelperplus.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.data.learning.DailyGoalSource
import com.riyadm.smalihelperplus.data.learning.LearningRepository
import com.riyadm.smalihelperplus.domain.learning.LearningProgressStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Content(val stats: LearningProgressStats, val dailyGoal: Int) : ProgressUiState
}

@HiltViewModel
class LearningProgressViewModel @Inject constructor(
    private val repository: LearningRepository,
    dailyGoalSource: DailyGoalSource,
) : ViewModel() {

    val state: StateFlow<ProgressUiState> = combine(repository.progressStats, dailyGoalSource.dailyGoal()) { stats, goal ->
        ProgressUiState.Content(stats, goal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState.Loading)

    init {
        // Picks up achievements whose inputs changed outside learning actions (e.g. a lowered daily goal).
        viewModelScope.launch { repository.evaluateAchievements() }
    }
}
