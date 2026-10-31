package com.riyadm.smalihelperplus.feature.updates

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.data.updates.AvailableUpdate
import com.riyadm.smalihelperplus.navigation.AppRoute
import java.text.DateFormat
import java.util.Date

@Suppress("UNUSED_PARAMETER")
@Composable
fun UpdatesScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: UpdatesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    DetailScaffold(title = "Documentation updates", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding().testTag("updates_list"),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { CheckCard(state, onCheck = viewModel::checkNow) }
            if (state.pending.isNotEmpty()) {
                item {
                    AvailableCard(
                        state = state,
                        onToggle = viewModel::toggleSelected,
                        onInstall = viewModel::installSelected,
                    )
                }
            }
            item { InstalledCard(state.installed) }
            item {
                SettingsCard(
                    state = state,
                    onAutoCheck = viewModel::setAutoCheck,
                    onUrlChange = viewModel::onUrlDraftChange,
                    onSaveUrl = viewModel::saveUrl,
                    onResetUrl = viewModel::resetUrl,
                    onRestore = { confirmRestore = true },
                )
            }
            item {
                Text(
                    "Updates only replace documentation content (opcodes, lessons, built-in snippets) after the " +
                        "download passes a checksum and format check. Your bookmarks, snippets and progress are never touched. " +
                        "Downloaded files are data only and are never executed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { Icon(Icons.Outlined.History, contentDescription = null) },
            title = { Text("Restore bundled content?") },
            text = {
                Text(
                    "This replaces the downloaded documentation with the version that shipped inside this app. " +
                        "Your bookmarks, snippets and learning progress are not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restoreBundled()
                    },
                    modifier = Modifier.testTag("updates_restore_confirm"),
                ) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CheckCard(state: UpdatesUiState, onCheck: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Check for updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        lastCheckedText(state.lastCheckMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (val banner = state.banner) {
                CheckBanner.UpToDate -> Banner(Icons.Outlined.CheckCircle, "Your documentation is up to date.", error = false)
                is CheckBanner.Failed -> Banner(Icons.Outlined.ErrorOutline, banner.message, error = true)
                is CheckBanner.AppUpdateRequired -> Banner(
                    Icons.Outlined.Info,
                    "This content needs a newer version of the app (version code ${banner.versionCode}). " +
                        "Your installed content keeps working.",
                    error = false,
                )
                null -> Unit
            }
            Button(
                onClick = onCheck,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("updates_check"),
            ) {
                if (state.checking) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp).semantics { contentDescription = "Checking for updates" },
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Checking...")
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Check now")
                }
            }
            Text(
                "Source: ${hostOf(state.savedManifestUrl.ifBlank { state.defaultManifestUrl })}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, error: Boolean) {
    val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("updates_banner")) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AvailableCard(state: UpdatesUiState, onToggle: (String) -> Unit, onInstall: () -> Unit) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth().testTag("updates_available"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.pending.size == 1) "1 update available" else "${state.pending.size} updates available",
                style = MaterialTheme.typography.titleMedium,
            )
            state.pending.forEach { update ->
                val checked = update.packageId in state.selectedIds
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle(update.packageId) },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("updates_select_${update.packageId}"),
                    )
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        Text(packageTitle(update.packageId), style = MaterialTheme.typography.titleSmall)
                        Text(
                            versionText(update) + " - " + Formatter.formatShortFileSize(context, update.sizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (update.description.isNotBlank()) {
                            Text(update.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Button(
                onClick = onInstall,
                enabled = !state.busy && state.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("updates_install"),
            ) {
                if (state.installing) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp).semantics { contentDescription = "Installing updates" },
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Installing...")
                } else {
                    Text(
                        when (state.selected.size) {
                            state.pending.size -> if (state.pending.size == 1) "Install update" else "Install all"
                            0 -> "Install"
                            else -> "Install ${state.selected.size} selected"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledCard(installed: List<ContentMetadataEntity>) {
    Card(Modifier.fillMaxWidth().testTag("updates_installed")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Installed documentation", style = MaterialTheme.typography.titleMedium)
            if (installed.isEmpty()) {
                Text(
                    "No documentation package is installed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            installed.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(packageTitle(item.packageId), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Version ${item.contentVersion} - updated ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.updatedAtMillis))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.description.isNotBlank()) {
                            Text(item.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(if (item.source == "remote") "Downloaded" else "Bundled") },
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    state: UpdatesUiState,
    onAutoCheck: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onSaveUrl: () -> Unit,
    onResetUrl: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Once a week on any network, when the battery is not low. Only checks - nothing is installed without you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = state.autoCheck,
                    onCheckedChange = onAutoCheck,
                    modifier = Modifier.testTag("updates_auto_check"),
                )
            }
            HorizontalDivider()
            Text("Advanced", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = state.urlDraft,
                onValueChange = onUrlChange,
                label = { Text("Custom manifest URL (HTTPS)") },
                placeholder = { Text("Leave empty for the default source") },
                isError = state.urlError != null,
                supportingText = { Text(state.urlError ?: "Default: ${hostOf(state.defaultManifestUrl)}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("updates_manifest_url"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSaveUrl,
                    enabled = state.urlDirty && state.urlError == null,
                    modifier = Modifier.testTag("updates_url_save"),
                ) { Text("Save") }
                if (state.savedManifestUrl.isNotBlank() || state.urlDraft.isNotBlank()) {
                    TextButton(onClick = onResetUrl) { Text("Use default") }
                }
            }
            HorizontalDivider()
            Text(
                "If an update causes problems you can return to the documentation that shipped with this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRestore,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("updates_restore"),
            ) {
                if (state.restoring) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp).semantics { contentDescription = "Restoring bundled content" },
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text("Restore bundled content")
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}

private fun versionText(update: AvailableUpdate): String =
    if (update.installedVersion == null) "New - version ${update.newVersion}" else "Version ${update.installedVersion} to ${update.newVersion}"

private fun lastCheckedText(millis: Long): String =
    if (millis <= 0L) "Never checked" else "Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))}"

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull().orEmpty().ifBlank { "custom source" }
