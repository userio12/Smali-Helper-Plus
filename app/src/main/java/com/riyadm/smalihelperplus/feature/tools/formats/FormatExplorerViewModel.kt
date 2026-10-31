package com.riyadm.smalihelperplus.feature.tools.formats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.database.dao.OpcodeDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Opcode shown in the format detail list. */
data class FormatOpcode(val name: String, val opcode: Int, val syntax: String, val summary: String)

data class FormatExplorerState(
    val loading: Boolean = true,
    /** Format id -> opcodes using it, sorted by opcode value. */
    val opcodesByFormat: Map<String, List<FormatOpcode>> = emptyMap(),
)

@HiltViewModel
class FormatExplorerViewModel @Inject constructor(
    opcodeDao: OpcodeDao,
    @DefaultDispatcher default: CoroutineDispatcher,
) : ViewModel() {
    val state: StateFlow<FormatExplorerState> = opcodeDao.observeAll()
        .map { list ->
            FormatExplorerState(
                loading = false,
                opcodesByFormat = list.groupBy { it.format }.mapValues { (_, ops) ->
                    ops.sortedWith(compareBy({ it.opcode }, { it.name }))
                        .map { FormatOpcode(it.name, it.opcode, it.syntax, it.summary) }
                },
            )
        }
        .flowOn(default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FormatExplorerState())
}
