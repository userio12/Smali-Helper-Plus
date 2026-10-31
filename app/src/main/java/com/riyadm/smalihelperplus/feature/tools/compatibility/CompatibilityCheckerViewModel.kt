package com.riyadm.smalihelperplus.feature.tools.compatibility

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.database.dao.OpcodeDao
import com.riyadm.smalihelperplus.domain.tools.CompatReport
import com.riyadm.smalihelperplus.domain.tools.CompatibilityChecker
import com.riyadm.smalihelperplus.domain.tools.OpcodeCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CompatUiState(
    val loading: Boolean = true,
    val minSdk: Int = DEFAULT_MIN_SDK,
    val code: String = "",
    /** Opcodes from the database that need more than API 1. */
    val newerOpcodes: List<OpcodeCompat> = emptyList(),
    val opcodeCount: Int = 0,
    val report: CompatReport? = null,
)

const val DEFAULT_MIN_SDK = 21

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompatibilityCheckerViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    opcodeDao: OpcodeDao,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    private val minSdk = savedState.getStateFlow(KEY_SDK, DEFAULT_MIN_SDK)
    private val code = savedState.getStateFlow(KEY_CODE, "")

    private class OpcodeSnapshot(val all: List<OpcodeCompat>, val newer: List<OpcodeCompat>)

    private val opcodes = opcodeDao.observeAll()
        .map { list ->
            val compat = list.map { OpcodeCompat(it.name, it.minApi, it.dexVersion) }
            OpcodeSnapshot(compat, CompatibilityChecker.newerThan(compat))
        }
        .flowOn(default)

    /** Heavy part: runs only for the debounced text, the target level or new opcode data. */
    private val analysis = combine(minSdk, code.debounce(DEBOUNCE_MS), opcodes) { sdk, text, ops ->
        CompatUiState(
            loading = false,
            minSdk = sdk,
            code = text,
            newerOpcodes = ops.newer,
            opcodeCount = ops.all.size,
            report = if (text.isBlank()) null else CompatibilityChecker.check(text, sdk, ops.all),
        )
    }.flowOn(default)

    val state: StateFlow<CompatUiState> = combine(analysis, code) { a, raw -> a.copy(code = raw) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompatUiState())

    fun setMinSdk(api: Int) {
        savedState[KEY_SDK] = api
    }

    fun setCode(text: String) {
        savedState[KEY_CODE] = text.take(MAX_CHARS)
    }

    private companion object {
        const val KEY_SDK = "minSdk"
        const val KEY_CODE = "code"
        const val DEBOUNCE_MS = 250L
        const val MAX_CHARS = 200_000
    }
}
