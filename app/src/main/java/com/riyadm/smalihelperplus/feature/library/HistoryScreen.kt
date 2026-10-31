package com.riyadm.smalihelperplus.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.navigation.AppRoute

/** "Recently viewed": opcodes, lessons and snippets grouped by Today / Yesterday / Earlier. */
@Composable
fun HistoryScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    DetailScaffold(
        title = "Recently viewed",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.total > 0) {
                IconButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("history_clear")) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding)) { ListSkeleton(rows = 6) }
            state.total == 0 -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = "Nothing viewed yet",
                    message = "Opcodes, lessons and snippets you open will appear here so you can jump back to them.",
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("history_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header_${group.bucket.name}") {
                        Text(
                            group.bucket.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(group.items, key = { "${it.type}:${it.targetId}" }) { item ->
                        HistoryRow(item) { viewModel.open(item, onNavigate) }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recently viewed?") },
            text = { Text("The list of recently viewed opcodes, lessons and snippets will be emptied. Bookmarks are not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        confirmClear = false
                    },
                    modifier = Modifier.testTag("history_clear_confirm"),
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(item: RecentViewEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("history_${item.type}_${item.targetId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(item.type)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = if (item.type == BookmarkType.OPCODE) FontFamily.Monospace else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subtitle.ifBlank { typeLabel(item.type) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
