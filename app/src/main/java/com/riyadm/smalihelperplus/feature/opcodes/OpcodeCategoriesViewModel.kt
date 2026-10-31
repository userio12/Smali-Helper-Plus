package com.riyadm.smalihelperplus.feature.opcodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.data.opcode.CategoryInfo
import com.riyadm.smalihelperplus.data.opcode.OpcodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class OpcodeCategoriesUiState(
    val loading: Boolean = true,
    val total: Int = 0,
    val categories: List<CategoryInfo> = emptyList(),
)

@HiltViewModel
class OpcodeCategoriesViewModel @Inject constructor(repository: OpcodeRepository) : ViewModel() {
    val uiState: StateFlow<OpcodeCategoriesUiState> = combine(
        repository.observeCategories(),
        repository.observeTotalCount(),
    ) { categories, total -> OpcodeCategoriesUiState(loading = false, total = total, categories = categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpcodeCategoriesUiState())
}
