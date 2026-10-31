package com.riyadm.smalihelperplus.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.datastore.EditorTheme
import com.riyadm.smalihelperplus.core.datastore.ThemeMode
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliHighlighter
import com.riyadm.smalihelperplus.core.designsystem.theme.codeColorsFor
import com.riyadm.smalihelperplus.core.designsystem.theme.isAppInDarkTheme
import com.riyadm.smalihelperplus.navigation.AboutRoute
import com.riyadm.smalihelperplus.navigation.AiAssistantRoute
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.BackupRoute
import com.riyadm.smalihelperplus.navigation.UpdatesRoute
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class ConfirmAction(val title: String, val message: String, val confirm: String) {
    SEARCH_HISTORY("Clear search history?", "Your recent searches will be removed from this device.", "Clear"),
    RECENT("Clear recently viewed?", "The list of recently opened opcodes, lessons and snippets will be emptied. Bookmarks are kept.", "Clear"),
    PROGRESS(
        "Reset learning progress?",
        "Lesson progress, quiz results, flashcard progress, your streak and achievements will be deleted. Bookmarks and snippets are kept. This cannot be undone.",
        "Reset",
    ),
}

private const val SAMPLE_CODE = ".method public static answer()I\n    const/4 v0, 0x2a\n    return v0\n.end method"

@Composable
fun SettingsScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf<ConfirmAction?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    DetailScaffold(title = "Settings", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        if (!state.loaded) return@DetailScaffold
        val prefs = state.prefs
        val darkApp = isAppInDarkTheme(prefs.themeMode)
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding().testTag("settings_list"),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section("Appearance") {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            Triple(ThemeMode.SYSTEM, "System", "settings_theme_system"),
                            Triple(ThemeMode.LIGHT, "Light", "settings_theme_light"),
                            Triple(ThemeMode.DARK, "Dark", "settings_theme_dark"),
                        )
                        modes.forEachIndexed { index, (mode, label, tag) ->
                            SegmentedButton(
                                selected = prefs.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                modifier = Modifier.testTag(tag),
                            ) { Text(label) }
                        }
                    }
                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    SwitchRow(
                        title = "Dynamic color",
                        description = if (dynamicSupported) "Use colors from your wallpaper." else "Needs Android 12 or newer. This device uses the app's own colors.",
                        checked = prefs.dynamicColor && dynamicSupported,
                        enabled = dynamicSupported,
                        onChange = viewModel::setDynamicColor,
                        tag = "settings_dynamic_color",
                    )
                }
            }
            item {
                EditorSection(
                    fontSize = prefs.editorFontSizeSp,
                    editorTheme = prefs.editorTheme,
                    darkApp = darkApp,
                    lineNumbers = prefs.editorShowLineNumbers,
                    wordWrap = prefs.editorWordWrap,
                    onFontSize = viewModel::setEditorFontSize,
                    onTheme = viewModel::setEditorTheme,
                    onLineNumbers = viewModel::setLineNumbers,
                    onWordWrap = viewModel::setWordWrap,
                )
            }
            item {
                Section("Learning") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Daily goal", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Learning activities per day that count towards your streak.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setDailyGoal(prefs.dailyGoal - 1) },
                            enabled = prefs.dailyGoal > 1,
                            modifier = Modifier.testTag("settings_daily_goal_dec"),
                        ) { Icon(Icons.Outlined.Remove, contentDescription = "Decrease daily goal") }
                        Text(
                            prefs.dailyGoal.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("settings_daily_goal_value"),
                        )
                        IconButton(
                            onClick = { viewModel.setDailyGoal(prefs.dailyGoal + 1) },
                            enabled = prefs.dailyGoal < 20,
                            modifier = Modifier.testTag("settings_daily_goal_inc"),
                        ) { Icon(Icons.Outlined.Add, contentDescription = "Increase daily goal") }
                    }
                }
            }
            item {
                Section("Documentation") {
                    SwitchRow(
                        title = "Check for updates automatically",
                        description = "Once a week, only on a connection and when the battery is not low. It only checks; nothing is installed without you.",
                        checked = prefs.autoUpdateChecks,
                        onChange = viewModel::setAutoUpdateChecks,
                        tag = "settings_auto_update",
                    )
                    HorizontalDivider()
                    NavRow(
                        title = "Documentation updates",
                        description = when {
                            state.pendingUpdates > 0 -> "${state.pendingUpdates} update(s) available"
                            prefs.lastUpdateCheckMillis > 0 -> "Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(prefs.lastUpdateCheckMillis))
                            else -> "Check now, view installed versions, restore bundled content"
                        },
                        tag = "settings_open_updates",
                        onClick = { onNavigate(UpdatesRoute) },
                    )
                }
            }
            item {
                AiSection(state = state, viewModel = viewModel, onOpenAssistant = { onNavigate(AiAssistantRoute) })
            }
            item {
                Section("Data") {
                    NavRow(
                        title = "Backup & restore",
                        description = "Export or import bookmarks, snippets and progress",
                        tag = "settings_open_backup",
                        onClick = { onNavigate(BackupRoute) },
                    )
                    HorizontalDivider()
                    NavRow("Clear search history", null, "settings_clear_search") { confirm = ConfirmAction.SEARCH_HISTORY }
                    NavRow("Clear recently viewed", null, "settings_clear_recent") { confirm = ConfirmAction.RECENT }
                    NavRow("Reset learning progress", null, "settings_reset_progress") { confirm = ConfirmAction.PROGRESS }
                }
            }
            item {
                Section("About") {
                    NavRow(
                        title = "About Smali Helper Plus",
                        description = "Version, credits, licenses and privacy",
                        tag = "settings_open_about",
                        onClick = { onNavigate(AboutRoute) },
                    )
                }
            }
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            ConfirmAction.SEARCH_HISTORY -> viewModel.clearSearchHistory()
                            ConfirmAction.RECENT -> viewModel.clearRecentlyViewed()
                            ConfirmAction.PROGRESS -> viewModel.resetLearningProgress()
                        }
                        confirm = null
                    },
                    modifier = Modifier.testTag("settings_confirm"),
                ) { Text(action.confirm) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorSection(
    fontSize: Float,
    editorTheme: EditorTheme,
    darkApp: Boolean,
    lineNumbers: Boolean,
    wordWrap: Boolean,
    onFontSize: (Float) -> Unit,
    onTheme: (EditorTheme) -> Unit,
    onLineNumbers: (Boolean) -> Unit,
    onWordWrap: (Boolean) -> Unit,
) {
    var sliderValue by remember(fontSize) { mutableFloatStateOf(fontSize) }
    val colors = codeColorsFor(editorTheme, darkApp)
    Section("Editor") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Font size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${sliderValue.roundToInt()} sp", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it.roundToInt().toFloat() },
            onValueChangeFinished = { onFontSize(sliderValue) },
            valueRange = 10f..24f,
            steps = 13,
            modifier = Modifier
                .testTag("settings_font_size")
                .semantics { contentDescription = "Editor font size"; stateDescription = "${sliderValue.roundToInt()} sp" },
        )
        Surface(shape = RoundedCornerShape(12.dp), color = colors.background, modifier = Modifier.fillMaxWidth().testTag("settings_font_preview")) {
            Text(
                SmaliHighlighter.highlight("const/4 v0, 0x1  # preview at ${sliderValue.roundToInt()} sp", colors),
                color = colors.text,
                fontFamily = FontFamily.Monospace,
                fontSize = sliderValue.sp,
                lineHeight = (sliderValue * 1.45f).sp,
                maxLines = 2,
                modifier = Modifier.padding(12.dp),
            )
        }
        HorizontalDivider()
        Text("Editor theme", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorTheme.entries.forEach { theme ->
                val themeColors = codeColorsFor(theme, darkApp)
                val selected = theme == editorTheme
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = themeColors.background,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, onClick = { onTheme(theme) }, role = Role.RadioButton)
                        .testTag("settings_editor_theme_${theme.name.lowercase()}"),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            theme.title + if (selected) "  (selected)" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = themeColors.text,
                        )
                        Text(
                            SmaliHighlighter.highlight(SAMPLE_CODE.lines().drop(1).take(2).joinToString("\n"), themeColors),
                            color = themeColors.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        SwitchRow("Line numbers", "Show a gutter with line numbers in the Playground.", lineNumbers, onLineNumbers, tag = "settings_line_numbers")
        SwitchRow("Word wrap", "Wrap long lines instead of scrolling horizontally.", wordWrap, onWordWrap, tag = "settings_word_wrap")
    }
}

@Composable
private fun AiSection(state: SettingsUiState, viewModel: SettingsViewModel, onOpenAssistant: () -> Unit) {
    Section("AI assistant (optional)") {
        Text(
            "Off by default. If you turn it on and add your own OpenAI-compatible service, questions and code you choose to " +
                "send go to that server over HTTPS - and only after you confirm each message that contains code. Nothing is built in, " +
                "no key ships with the app, and the rest of the app never needs the network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            "Enable AI assistant", null, state.ai.enabled, viewModel::setAiEnabled, tag = "settings_ai_enabled",
        )
        OutlinedTextField(
            value = state.endpointDraft,
            onValueChange = viewModel::onEndpointChange,
            label = { Text("Endpoint base URL") },
            placeholder = { Text("https://api.example.com/v1") },
            supportingText = { Text(state.endpointError?.let { "Invalid: $it" } ?: "Requests go to {base}/chat/completions") },
            isError = state.endpointError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("settings_ai_endpoint"),
        )
        OutlinedTextField(
            value = state.modelDraft,
            onValueChange = viewModel::onModelChange,
            label = { Text("Model name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_ai_model"),
        )
        OutlinedTextField(
            value = state.apiKeyDraft,
            onValueChange = viewModel::onApiKeyChange,
            label = { Text(if (state.ai.hasKey) "New API key (leave empty to keep the saved one)" else "API key") },
            supportingText = {
                Text(
                    if (state.ai.hasKey) "A key is saved, encrypted with the Android Keystore on this device." else "Stored encrypted on this device only. Never backed up.",
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("settings_ai_key"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = viewModel::saveAi, enabled = state.aiDirty && state.endpointError == null, modifier = Modifier.testTag("settings_ai_save")) {
                Text("Save")
            }
            if (state.ai.hasKey) {
                OutlinedButton(onClick = viewModel::clearApiKey, modifier = Modifier.testTag("settings_ai_clear_key")) { Text("Remove key") }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = state.test != AiTestState.Running,
                modifier = Modifier.testTag("settings_ai_test"),
            ) { Text("Test connection") }
            when (val test = state.test) {
                AiTestState.Idle -> Unit
                AiTestState.Running -> CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Testing connection" }, strokeWidth = 2.dp)
                is AiTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Connected to ${test.host}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("settings_ai_test_result"))
                }
                is AiTestState.Failure -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(test.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("settings_ai_test_result"))
                }
            }
        }
        Text(
            "The test sends one short fixed message (no code, no personal data) to the server above.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        SwitchRow(
            "Always ask before sending",
            "Ask for confirmation before every message. Messages containing code always ask, whatever this is set to.",
            state.ai.alwaysAsk, viewModel::setAiAlwaysAsk, tag = "settings_ai_always_ask",
        )
        if (state.ai.isConfigured) {
            NavRow("Open AI assistant", null, "settings_open_ai", onOpenAssistant)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun NavRow(title: String, description: String?, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
