package com.riyadm.smalihelperplus.feature.playground

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.copyToClipboard
import com.riyadm.smalihelperplus.core.designsystem.theme.LocalEditorColors
import com.riyadm.smalihelperplus.core.smali.SmaliSamples
import com.riyadm.smalihelperplus.core.smali.diagnostics.Diagnostic
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity
import com.riyadm.smalihelperplus.feature.playground.editor.CodeEditor
import com.riyadm.smalihelperplus.feature.playground.editor.EditorDecoration
import com.riyadm.smalihelperplus.feature.playground.editor.EditorLogic
import com.riyadm.smalihelperplus.feature.playground.editor.keyboardAwarePadding
import com.riyadm.smalihelperplus.feature.playground.editor.rememberEditorController
import com.riyadm.smalihelperplus.navigation.AiAssistantRoute
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.ControlFlowRoute
import com.riyadm.smalihelperplus.navigation.MethodExplorerRoute
import com.riyadm.smalihelperplus.navigation.RegisterVisualizerRoute
import com.riyadm.smalihelperplus.navigation.SmaliExplainerRoute
import com.riyadm.smalihelperplus.navigation.SnippetLibraryRoute
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class PlaygroundSheet { DIAGNOSTICS, OUTLINE, RECENT, SAMPLES }

@Composable
fun PlaygroundScreen(onNavigate: (AppRoute) -> Unit) {
    val viewModel: PlaygroundViewModel = hiltViewModel()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val recent by viewModel.recentFiles.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val value = viewModel.editorValue
    val text = value.text
    val colors = LocalEditorColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val controller = rememberEditorController()

    var menuOpen by remember { mutableStateOf(false) }
    var sendMenuOpen by remember { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<PlaygroundSheet?>(null) }
    var showFind by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var currentMatch by rememberSaveable { mutableIntStateOf(0) }
    var showSaveSnippet by rememberSaveable { mutableStateOf(false) }

    val fontSize = prefs?.editorFontSizeSp ?: 14f
    val wordWrap = prefs?.editorWordWrap ?: false
    val showLineNumbers = prefs?.editorShowLineNumbers ?: true

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PlaygroundEvent.Message -> {
                    val result = snackbar.showSnackbar(
                        message = event.text,
                        actionLabel = if (event.offerUndo) "Undo" else null,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                }
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.openDocument(uri)
    }
    // octet-stream: with text/plain the system file picker appends ".txt" to the suggested "Name.smali".
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.saveDocument(uri)
    }

    // Search matches and decorations.
    val matches = remember(text, query, matchCase, showFind) {
        if (showFind) EditorLogic.findMatches(text, query, matchCase) else emptyList()
    }
    val activeMatch = if (currentMatch < matches.size) currentMatch else 0
    val selectedRegister = remember(text, value.selection) {
        if (value.selection.collapsed) EditorLogic.registerAt(text, value.selection.start) else null
    }
    val registerRanges = remember(text, selectedRegister) {
        selectedRegister?.let { EditorLogic.registerOccurrences(text, it) }.orEmpty()
    }
    val bracket = remember(text, value.selection) {
        if (value.selection.collapsed) EditorLogic.matchingBracket(text, value.selection.start) else null
    }
    val lineStarts = remember(text) { EditorLogic.lineStarts(text) }
    val diagnosticRanges = remember(analysis, lineStarts, text) {
        if (analysis.analyzedLength != text.length) emptyList() else analysis.diagnostics
            .filter { it.severity != Severity.INFO && it.endColumn > it.startColumn && it.line - 1 < lineStarts.size }
            .take(500)
            .map { d ->
                val base = lineStarts[d.line - 1]
                EditorDecoration(base + d.startColumn until base + d.endColumn, (if (d.severity == Severity.ERROR) colors.error else colors.number).copy(alpha = 0.16f), underline = true)
            }
    }
    val decorations = remember(matches, activeMatch, registerRanges, bracket, diagnosticRanges, colors) {
        buildList {
            addAll(diagnosticRanges)
            matches.forEachIndexed { i, r -> add(EditorDecoration(r, if (i == activeMatch) colors.selection else colors.highlight)) }
            registerRanges.forEach { add(EditorDecoration(it, colors.register.copy(alpha = 0.18f))) }
            bracket?.let { (a, b) ->
                add(EditorDecoration(a..a, colors.opcode.copy(alpha = 0.25f)))
                add(EditorDecoration(b..b, colors.opcode.copy(alpha = 0.25f)))
            }
        }
    }
    val markers = remember(analysis) {
        val m = HashMap<Int, Severity>()
        analysis.diagnostics.forEach { d ->
            val line = d.line - 1
            val current = m[line]
            if (current == null || d.severity.ordinal < current.ordinal) m[line] = d.severity
        }
        m
    }
    val completion = remember(text, value.selection, controller.focused) {
        if (controller.focused && value.selection.collapsed) EditorLogic.completionAt(text, value.selection.start) else null
    }

    fun jumpTo(offset: Int, end: Int = offset) {
        viewModel.select(offset, end)
        scope.launch { controller.reveal(offset, center = true) }
    }

    fun jumpToLine(line: Int, column: Int = 0) {
        val idx = (line - 1).coerceIn(0, lineStarts.size - 1)
        jumpTo((lineStarts[idx] + column).coerceAtMost(text.length))
    }

    fun goToMatch(index: Int) {
        if (matches.isEmpty()) return
        val i = ((index % matches.size) + matches.size) % matches.size
        currentMatch = i
        jumpTo(matches[i].first, matches[i].last + 1)
    }

    fun send(target: String, route: AppRoute) {
        viewModel.sendTo(target)
        onNavigate(route)
    }

    // Landscape phone with the keyboard open: the toolbar and status row would leave the editor less than one line,
    // so they step aside while typing (they come back as soon as the keyboard closes).
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val shortWindow = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } < 480.dp
    val typingInShortWindow = imeVisible && shortWindow

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!typingInShortWindow) TopAppBar(
                title = {
                    Column {
                        Text("Playground", maxLines = 1)
                        Text(
                            viewModel.fileName ?: "Unsaved buffer",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo, modifier = Modifier.testTag("playground_undo")) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo, modifier = Modifier.testTag("playground_redo")) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = viewModel::format, modifier = Modifier.testTag("playground_format")) {
                        Icon(Icons.AutoMirrored.Outlined.FormatAlignLeft, contentDescription = "Format code")
                    }
                    IconButton(onClick = { showFind = !showFind }, modifier = Modifier.testTag("playground_find")) {
                        Icon(Icons.Outlined.FindReplace, contentDescription = "Find and replace")
                    }
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("playground_more")) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    PlaygroundMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        wordWrap = wordWrap,
                        showLineNumbers = showLineNumbers,
                        onAction = { action ->
                            menuOpen = false
                            when (action) {
                                MenuAction.OPEN -> openLauncher.launch(arrayOf("*/*"))
                                MenuAction.SAVE -> saveLauncher.launch(viewModel.fileName?.takeIf { it.endsWith(".smali") } ?: "Snippet.smali")
                                MenuAction.RECENT -> sheet = PlaygroundSheet.RECENT
                                MenuAction.SAVE_SNIPPET -> showSaveSnippet = true
                                MenuAction.SAMPLES -> sheet = PlaygroundSheet.SAMPLES
                                MenuAction.OUTLINE -> sheet = PlaygroundSheet.OUTLINE
                                MenuAction.COPY -> copyToClipboard(context, "Smali code", text)
                                MenuAction.PASTE -> {
                                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                    val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                                    if (pasted.isNullOrEmpty()) scope.launch { snackbar.showSnackbar("The clipboard is empty") } else viewModel.insertAtCursor(pasted)
                                }
                                MenuAction.CLEAR -> viewModel.clear()
                                MenuAction.WRAP -> viewModel.setWordWrap(!wordWrap)
                                MenuAction.LINE_NUMBERS -> viewModel.setShowLineNumbers(!showLineNumbers)
                                MenuAction.FONT_UP -> viewModel.changeFontSize(1f)
                                MenuAction.FONT_DOWN -> viewModel.changeFontSize(-1f)
                                MenuAction.SEND -> sendMenuOpen = true
                                MenuAction.LIBRARY -> onNavigate(SnippetLibraryRoute)
                            }
                        },
                    )
                    DropdownMenu(expanded = sendMenuOpen, onDismissRequest = { sendMenuOpen = false }) {
                        Text("Send code to", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        SendItem("Smali explainer", Icons.Outlined.Lightbulb, "playground_send_explainer") { sendMenuOpen = false; send(CodeHandoff.EXPLAINER, SmaliExplainerRoute) }
                        SendItem("Register visualizer", Icons.Outlined.GridView, "playground_send_registers") { sendMenuOpen = false; send(CodeHandoff.REGISTER_VISUALIZER, RegisterVisualizerRoute) }
                        SendItem("Method explorer", Icons.Outlined.Schema, "playground_send_methods") { sendMenuOpen = false; send(CodeHandoff.METHOD_EXPLORER, MethodExplorerRoute) }
                        SendItem("Control flow", Icons.Outlined.AccountTree, "playground_send_cfg") { sendMenuOpen = false; send(CodeHandoff.CONTROL_FLOW, ControlFlowRoute) }
                        SendItem("AI assistant", Icons.Outlined.AutoAwesome, "playground_send_ai") { sendMenuOpen = false; send(CodeHandoff.AI, AiAssistantRoute) }
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .keyboardAwarePadding(),
        ) {
            AnimatedVisibility(visible = showFind) {
                FindReplaceBar(
                    query = query,
                    onQueryChange = { query = it; currentMatch = 0 },
                    replacement = replacement,
                    onReplacementChange = { replacement = it },
                    matchCase = matchCase,
                    onMatchCaseChange = { matchCase = it },
                    matchCount = matches.size,
                    current = if (matches.isEmpty()) 0 else activeMatch + 1,
                    onPrevious = { goToMatch(activeMatch - 1) },
                    onNext = { goToMatch(if (matches.getOrNull(activeMatch)?.first == value.selection.min) activeMatch + 1 else activeMatch) },
                    onReplace = {
                        val m = matches.getOrNull(activeMatch)
                        if (m != null) viewModel.replaceRange(m, replacement)
                    },
                    onReplaceAll = { viewModel.replaceAllMatches(query, replacement, matchCase) },
                    onClose = { showFind = false },
                )
            }
            if (text.isEmpty() && viewModel.restored) {
                EmptyState(
                    icon = Icons.Outlined.Code,
                    title = "The editor is empty",
                    message = "Start typing smali, open a .smali file or load the starter sample.",
                    actionLabel = "Load starter sample",
                    onAction = { viewModel.loadSample(SmaliSamples.all.first()) },
                    modifier = Modifier.testTag("playground_empty"),
                )
            }
            CodeEditor(
                value = value,
                onValueChange = viewModel::onValueChange,
                controller = controller,
                colors = colors,
                fontSizeSp = fontSize,
                showLineNumbers = showLineNumbers,
                wordWrap = wordWrap,
                decorations = decorations,
                lineMarkers = markers,
                onGutterTap = { line ->
                    val d = analysis.diagnostics.firstOrNull { it.line == line + 1 }
                    if (d != null) scope.launch { snackbar.showSnackbar("Line ${d.line}: ${d.message}") }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (completion != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("playground_completions"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    completion.suggestions.forEach { s ->
                        SuggestionChip(
                            onClick = { viewModel.applyCompletion(completion, s) },
                            label = { Text(s, fontFamily = FontFamily.Monospace) },
                        )
                    }
                }
            }
            if (!typingInShortWindow) StatusBar(
                errors = analysis.errors,
                warnings = analysis.warnings,
                infos = analysis.diagnostics.size - analysis.errors - analysis.warnings,
                line = EditorLogic.lineOf(lineStarts, value.selection.end.coerceIn(0, text.length)) + 1,
                column = value.selection.end - lineStarts[EditorLogic.lineOf(lineStarts, value.selection.end.coerceIn(0, text.length))] + 1,
                register = selectedRegister,
                registerCount = registerRanges.size,
                onDiagnostics = { sheet = PlaygroundSheet.DIAGNOSTICS },
                onOutline = { sheet = PlaygroundSheet.OUTLINE },
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    when (sheet) {
        PlaygroundSheet.DIAGNOSTICS -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            DiagnosticsSheet(analysis.diagnostics) { d ->
                sheet = null
                jumpToLine(d.line, d.startColumn)
            }
        }
        PlaygroundSheet.OUTLINE -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            OutlineSheet(analysis) { line ->
                sheet = null
                jumpToLine(line)
            }
        }
        PlaygroundSheet.RECENT -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            RecentFilesSheet(
                files = recent,
                onOpen = { sheet = null; viewModel.openDocument(it.toUri(), fromRecents = true) },
                onRemove = viewModel::removeRecentFile,
                onClear = viewModel::clearRecentFiles,
            )
        }
        PlaygroundSheet.SAMPLES -> ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = sheetState) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Load a sample", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Text(
                    "Replaces the editor content (you can undo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SmaliSamples.all.forEach { s ->
                    ListItem(
                        headlineContent = { Text(s.title) },
                        supportingContent = { Text("${s.code.lines().size} lines") },
                        leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                        modifier = Modifier.clickable { sheet = null; viewModel.loadSample(s) }.testTag("playground_sample_${s.id}"),
                    )
                }
            }
        }
        null -> Unit
    }

    if (showSaveSnippet) {
        SaveSnippetDialog(
            collections = collections,
            initialTitle = viewModel.fileName?.removeSuffix(".smali") ?: analysis.outline?.simpleName ?: "",
            onDismiss = { showSaveSnippet = false },
            onSave = { title, collection, description ->
                showSaveSnippet = false
                viewModel.saveSnippet(title, collection, description)
            },
        )
    }
}

private enum class MenuAction { OPEN, SAVE, RECENT, SAVE_SNIPPET, SAMPLES, OUTLINE, COPY, PASTE, CLEAR, WRAP, LINE_NUMBERS, FONT_UP, FONT_DOWN, SEND, LIBRARY }

@Composable
private fun PlaygroundMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    wordWrap: Boolean,
    showLineNumbers: Boolean,
    onAction: (MenuAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.testTag("playground_menu")) {
        MenuItem("Open .smali file", Icons.Outlined.FileOpen, "playground_open") { onAction(MenuAction.OPEN) }
        MenuItem("Save / export…", Icons.Outlined.Save, "playground_save") { onAction(MenuAction.SAVE) }
        MenuItem("Recent files", Icons.Outlined.History, "playground_recent") { onAction(MenuAction.RECENT) }
        MenuItem("Save as snippet", Icons.Outlined.BookmarkAdd, "playground_save_snippet") { onAction(MenuAction.SAVE_SNIPPET) }
        MenuItem("Load sample", Icons.Outlined.Code, "playground_samples") { onAction(MenuAction.SAMPLES) }
        HorizontalDivider()
        MenuItem("Outline", Icons.AutoMirrored.Outlined.ViewList, "playground_outline_menu") { onAction(MenuAction.OUTLINE) }
        MenuItem("Copy all", Icons.Outlined.ContentCopy, "playground_copy") { onAction(MenuAction.COPY) }
        MenuItem("Paste", Icons.Outlined.ContentPaste, "playground_paste") { onAction(MenuAction.PASTE) }
        MenuItem("Clear editor", Icons.Outlined.DeleteSweep, "playground_clear") { onAction(MenuAction.CLEAR) }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Word wrap") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.WrapText, contentDescription = null) },
            trailingIcon = { Checkbox(checked = wordWrap, onCheckedChange = null) },
            onClick = { onAction(MenuAction.WRAP) },
            modifier = Modifier.testTag("playground_wrap"),
        )
        DropdownMenuItem(
            text = { Text("Line numbers") },
            leadingIcon = { Icon(Icons.Outlined.FormatListNumbered, contentDescription = null) },
            trailingIcon = { Checkbox(checked = showLineNumbers, onCheckedChange = null) },
            onClick = { onAction(MenuAction.LINE_NUMBERS) },
            modifier = Modifier.testTag("playground_line_numbers"),
        )
        MenuItem("Larger text", Icons.Outlined.TextIncrease, "playground_font_up") { onAction(MenuAction.FONT_UP) }
        MenuItem("Smaller text", Icons.Outlined.TextDecrease, "playground_font_down") { onAction(MenuAction.FONT_DOWN) }
        HorizontalDivider()
        MenuItem("Send to…", Icons.AutoMirrored.Outlined.Send, "playground_send") { onAction(MenuAction.SEND) }
        MenuItem("Snippet library", Icons.AutoMirrored.Outlined.LibraryBooks, "playground_library") { onAction(MenuAction.LIBRARY) }
    }
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun SendItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) =
    MenuItem(label, icon, tag, onClick)

@Composable
private fun FindReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replacement: String,
    onReplacementChange: (String) -> Unit,
    matchCase: Boolean,
    onMatchCaseChange: (Boolean) -> Unit,
    matchCount: Int,
    current: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().testTag("playground_find_bar")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Find") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f).testTag("playground_find_query"),
                    supportingText = null,
                )
                Text(
                    if (query.isEmpty()) "" else "$current/$matchCount",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("playground_find_count"),
                )
                IconButton(onClick = onPrevious, enabled = matchCount > 0, modifier = Modifier.testTag("playground_find_prev")) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = onNext, enabled = matchCount > 0, modifier = Modifier.testTag("playground_find_next")) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close find bar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = replacement,
                    onValueChange = onReplacementChange,
                    placeholder = { Text("Replace with") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f).testTag("playground_replace_field"),
                )
                FilterChip(
                    selected = matchCase,
                    onClick = { onMatchCaseChange(!matchCase) },
                    label = { Text("Aa") },
                    modifier = Modifier.testTag("playground_match_case"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReplace, enabled = matchCount > 0, modifier = Modifier.testTag("playground_replace")) { Text("Replace") }
                TextButton(onClick = onReplaceAll, enabled = matchCount > 0, modifier = Modifier.testTag("playground_replace_all")) { Text("Replace all") }
            }
        }
    }
}

@Composable
private fun StatusBar(
    errors: Int,
    warnings: Int,
    infos: Int,
    line: Int,
    column: Int,
    register: String?,
    registerCount: Int,
    onDiagnostics: () -> Unit,
    onOutline: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, label, tint) = when {
                errors > 0 -> Triple(Icons.Outlined.ErrorOutline, "$errors error${if (errors == 1) "" else "s"}" + if (warnings > 0) " · $warnings warning${if (warnings == 1) "" else "s"}" else "", MaterialTheme.colorScheme.error)
                warnings > 0 -> Triple(Icons.Outlined.WarningAmber, "$warnings warning${if (warnings == 1) "" else "s"}", MaterialTheme.colorScheme.tertiary)
                infos > 0 -> Triple(Icons.Outlined.Info, "$infos note${if (infos == 1) "" else "s"}", MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Triple(Icons.Outlined.CheckCircle, "No problems", MaterialTheme.colorScheme.primary)
            }
            AssistChip(
                onClick = onDiagnostics,
                label = { Text(label) },
                leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("playground_diagnostics"),
            )
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = onOutline,
                label = { Text("Outline") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("playground_outline"),
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Ln $line, Col $column", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (register != null) {
                    Text("$register × $registerCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSheet(diagnostics: List<Diagnostic>, onJump: (Diagnostic) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("playground_diagnostics_sheet")) {
        Text("Problems", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(
            "Static analysis only: the code is never assembled or executed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (diagnostics.isEmpty()) {
            EmptyState(icon = Icons.Outlined.CheckCircle, title = "No problems found", message = "The analyser found no errors, warnings or notes.")
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(diagnostics) { d -> DiagnosticRow(d, onClick = { onJump(d) }) }
            }
        }
    }
}

@Composable
fun DiagnosticRow(d: Diagnostic, onClick: (() -> Unit)?) {
    val (icon, tint) = when (d.severity) {
        Severity.ERROR -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
        Severity.WARNING -> Icons.Outlined.WarningAmber to MaterialTheme.colorScheme.tertiary
        Severity.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        headlineContent = { Text(d.message) },
        overlineContent = { Text("Line ${d.line}" + if (d.endColumn > d.startColumn) ", col ${d.startColumn + 1}" else "") },
        supportingContent = d.suggestion?.let { s -> { Text("Fix: $s", color = MaterialTheme.colorScheme.primary) } },
        leadingContent = { Icon(icon, contentDescription = d.severity.name.lowercase(), tint = tint) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun OutlineSheet(analysis: EditorAnalysis, onJump: (Int) -> Unit) {
    val o = analysis.outline
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("playground_outline_sheet")) {
        Text("Outline", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (o == null || (o.descriptor == null && o.methods.isEmpty() && o.fields.isEmpty())) {
            EmptyState(icon = Icons.AutoMirrored.Outlined.ViewList, title = "Nothing to outline", message = "Add a .class directive, fields or methods to see the structure here.")
            return@Column
        }
        Text(
            "Tap an entry to jump to it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn {
            if (o.descriptor != null) {
                item {
                    ListItem(
                        headlineContent = { Text(o.javaHeader, fontFamily = FontFamily.Monospace) },
                        supportingContent = { Text(o.descriptor) },
                        leadingContent = { Icon(Icons.Outlined.Schema, contentDescription = null) },
                        modifier = Modifier.clickable { onJump(1) },
                    )
                }
            }
            items(o.fields) { f ->
                ListItem(
                    headlineContent = { Text(f.javaDeclaration, fontFamily = FontFamily.Monospace) },
                    overlineContent = { Text("Field · line ${f.line}") },
                    modifier = Modifier.clickable { onJump(f.line) },
                )
            }
            items(o.methods) { m ->
                ListItem(
                    headlineContent = { Text(m.javaSignature, fontFamily = FontFamily.Monospace) },
                    overlineContent = { Text("Method · line ${m.startLine}") },
                    supportingContent = { Text("${m.instructionCount} instructions" + (m.registers?.let { " · $it registers" } ?: "")) },
                    modifier = Modifier.clickable { onJump(m.startLine) },
                )
            }
        }
    }
}

@Composable
private fun RecentFilesSheet(
    files: List<com.riyadm.smalihelperplus.core.database.entity.RecentFileEntity>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("playground_recent_sheet")) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (files.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear all") }
        }
        if (files.isEmpty()) {
            EmptyState(icon = Icons.Outlined.History, title = "No recent files", message = "Files you open or save from the Playground appear here.")
        } else {
            val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            LazyColumn {
                items(files, key = { it.uri }) { f ->
                    ListItem(
                        headlineContent = { Text(f.displayName) },
                        supportingContent = { Text(format.format(Date(f.openedAtMillis))) },
                        leadingContent = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(f.uri) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove ${f.displayName} from recent files") }
                        },
                        modifier = Modifier.clickable { onOpen(f.uri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveSnippetDialog(
    collections: List<String>,
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, collection: String, description: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var collection by rememberSaveable { mutableStateOf("My snippets") }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as snippet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true,
                    isError = title.isBlank(), modifier = Modifier.fillMaxWidth().testTag("snippet_title"),
                )
                OutlinedTextField(
                    value = collection, onValueChange = { collection = it }, label = { Text("Collection") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("snippet_collection"),
                )
                if (collections.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        collections.forEach { c ->
                            FilterChip(selected = c == collection, onClick = { collection = c }, label = { Text(c) })
                        }
                    }
                }
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, collection, description) }, enabled = title.isNotBlank(), modifier = Modifier.testTag("snippet_save")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
