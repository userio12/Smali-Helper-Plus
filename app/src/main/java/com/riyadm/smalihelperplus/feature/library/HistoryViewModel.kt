package com.riyadm.smalihelperplus.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.data.library.HistoryGroup
import com.riyadm.smalihelperplus.data.library.HistoryGrouping
import com.riyadm.smalihelperplus.data.library.HistoryRepository
import com.riyadm.smalihelperplus.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val groups: List<HistoryGroup> = emptyList(),
    val total: Int = 0,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val opener: LibraryItemOpener,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = repository.observeRecentViews().map { items ->
        HistoryUiState(
            loading = false,
            groups = HistoryGrouping.group(items, System.currentTimeMillis()),
            total = items.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    fun clearAll() {
        viewModelScope.launch {
            repository.clearViews()
            messageChannel.send("History cleared")
        }
    }

    fun open(item: RecentViewEntity, navigate: (AppRoute) -> Unit) {
        viewModelScope.launch {
            val route = opener.resolve(item.type, item.targetId)
            if (route != null) navigate(route) else messageChannel.send("\"${item.title}\" no longer exists")
        }
    }
}
