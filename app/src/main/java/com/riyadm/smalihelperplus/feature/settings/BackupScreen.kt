package com.riyadm.smalihelperplus.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.data.backup.BackupCounts
import com.riyadm.smalihelperplus.data.backup.BackupRepository
import com.riyadm.smalihelperplus.data.backup.ImportMode
import com.riyadm.smalihelperplus.navigation.AppRoute

private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@Suppress("UNUSED_PARAMETER")
@Composable
fun BackupScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: BackupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    val exportFull = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.export(it, bookmarksOnly = false)
    }
    val exportBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.export(it, bookmarksOnly = true)
    }
    val importFull = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onImportPicked(it, bookmarksOnly = false)
    }
    val importBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onImportPicked(it, bookmarksOnly = true)
    }

    DetailScaffold(title = "Backup & restore", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().testTag("backup_list"),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.busy) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("backup_progress")) }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Your data stays on your device", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "A backup is a JSON file you save wherever you like (Files, Drive, USB). It contains your bookmarks, " +
                                "your own snippets, learning progress, quiz results, flashcard progress, study days, achievements, " +
                                "search history and recently viewed items. It never contains documentation content or any AI key.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.counts.total > 0) {
                            Text(
                                "Currently stored: " + state.counts.lines().joinToString { (label, count) -> storedCount(label, count) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                ActionCard(
                    title = "Full backup",
                    description = "Export everything above, or restore it from a backup file.",
                    exportTag = "backup_export",
                    importTag = "backup_import",
                    exportLabel = "Export backup",
                    importLabel = "Import backup",
                    busy = state.busy,
                    onExport = { exportFull.launch(BackupRepository.defaultFileName(bookmarksOnly = false)) },
                    onImport = { importFull.launch(IMPORT_MIME_TYPES) },
                )
            }
            item {
                ActionCard(
                    title = "Bookmarks only",
                    description = "Share or move just your bookmarks. Importing bookmarks adds new ones and never removes anything.",
                    exportTag = "backup_export_bookmarks",
                    importTag = "backup_import_bookmarks",
                    exportLabel = "Export bookmarks",
                    importLabel = "Import bookmarks",
                    busy = state.busy,
                    onExport = { exportBookmarks.launch(BackupRepository.defaultFileName(bookmarksOnly = true)) },
                    onImport = { importBookmarks.launch(IMPORT_MIME_TYPES) },
                    icon = Icons.Outlined.BookmarkBorder,
                )
            }
            state.lastSummary?.let { summary ->
                item {
                    Card(
                        Modifier.fillMaxWidth().testTag("backup_summary"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Last import", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (summary.mode == ImportMode.REPLACE) "Replaced your data with the backup." else "Merged the backup into your data.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val lines = summary.written.lines()
                            if (lines.isEmpty()) {
                                Text("Nothing new was added - everything in the file already existed.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                lines.forEach { (label, count) -> Text("$label: $count", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text(if (pending.bookmarksOnly) "Import bookmarks" else "Import backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("The file contains:", style = MaterialTheme.typography.bodyMedium)
                    CountsList(pending.counts)
                    if (pending.bookmarksOnly) {
                        Text(
                            "Bookmarks that you do not have yet will be added. Nothing is removed or overwritten.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Column(Modifier.selectableGroup()) {
                            ModeRow(
                                selected = pending.mode == ImportMode.MERGE,
                                title = "Merge (recommended)",
                                description = "Keep your current data and add what is new. Progress keeps the better value.",
                                tag = "backup_mode_merge",
                                onClick = { viewModel.setMode(ImportMode.MERGE) },
                            )
                            ModeRow(
                                selected = pending.mode == ImportMode.REPLACE,
                                title = "Replace",
                                description = "Delete your current bookmarks, snippets, progress and history first, then restore the backup.",
                                tag = "backup_mode_replace",
                                onClick = { viewModel.setMode(ImportMode.REPLACE) },
                            )
                        }
                        if (pending.mode == ImportMode.REPLACE) {
                            Text(
                                "This cannot be undone. Export a backup first if you are not sure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport, modifier = Modifier.testTag("backup_import_confirm")) {
                    Text(if (!pending.bookmarksOnly && pending.mode == ImportMode.REPLACE) "Replace my data" else "Import")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    exportTag: String,
    importTag: String,
    exportLabel: String,
    importLabel: String,
    busy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, enabled = !busy, modifier = Modifier.weight(1f).testTag(exportTag)) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(exportLabel, maxLines = 1)
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f).testTag(importTag)) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(importLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CountsList(counts: BackupCounts) {
    Column {
        counts.lines().forEach { (label, count) -> Text("$count  $label", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ModeRow(selected: Boolean, title: String, description: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "1 bookmark", "3 snippets", "2 lesson progress" for the inline summary (labels come from [BackupCounts.lines]). */
private fun storedCount(label: String, count: Int): String {
    val noun = label.removePrefix("Your ").lowercase()
    val singular = if (count == 1 && noun.endsWith("s") && !noun.endsWith("ss")) noun.dropLast(1) else noun
    return "$count $singular"
}
