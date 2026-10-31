package com.riyadm.smalihelperplus.feature.tools.compare

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliHighlighter
import com.riyadm.smalihelperplus.core.designsystem.component.copyToClipboard
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeColors
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeTextStyle
import com.riyadm.smalihelperplus.core.designsystem.theme.LocalCodeColors
import com.riyadm.smalihelperplus.domain.tools.DiffKind
import com.riyadm.smalihelperplus.domain.tools.DiffResult
import com.riyadm.smalihelperplus.domain.tools.SideCell
import com.riyadm.smalihelperplus.domain.tools.SideRow
import com.riyadm.smalihelperplus.domain.tools.TextSpan
import com.riyadm.smalihelperplus.domain.tools.UnifiedLine
import com.riyadm.smalihelperplus.feature.tools.common.CodeInputField
import com.riyadm.smalihelperplus.feature.tools.common.NoteRow
import com.riyadm.smalihelperplus.feature.tools.common.ToolCard
import com.riyadm.smalihelperplus.navigation.AppRoute

private val AddedBg = Color(0xFF2E7D32)
private val RemovedBg = Color(0xFFC62828)

@Composable
fun CodeComparisonScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: CodeComparisonViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val leftPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(CompareSide.LEFT, uri)
    }
    val rightPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(CompareSide.RIGHT, uri)
    }
    val mimeTypes = arrayOf("text/*", "application/octet-stream")

    fun paste(side: CompareSide) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrEmpty()) viewModel.setText(side, "") else viewModel.setText(side, text)
    }

    val colors = LocalCodeColors.current
    val result = state.result

    DetailScaffold(title = "Code comparison", onBack = onBack, snackbarHostState = snackbar) { padding ->
        BoxWithConstraints(Modifier.fillMaxWidth().padding(padding).consumeWindowInsets(padding).imePadding()) {
            val totalWidth = maxWidth
            val hScroll = rememberScrollState()
            val maxLen = remember(result) {
                when {
                    result == null -> 0
                    else -> result.unified.maxOfOrNull { it.text.length } ?: 0
                }
            }
            val codeWidth: Dp = maxOf(totalWidth - 100.dp, (maxLen * 8 + 24).dp)
            val cellWidth: Dp = maxOf(totalWidth / 2, (maxLen * 8 + 64).dp)

            LazyColumn(
                Modifier.fillMaxWidth().testTag("compare_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("left") {
                    SideInput(
                        title = sideTitle("Left", state.leftName),
                        text = state.left,
                        tag = "compare_left",
                        onChange = { viewModel.setText(CompareSide.LEFT, it) },
                        onPaste = { paste(CompareSide.LEFT) },
                        onPlayground = { viewModel.usePlaygroundCode(CompareSide.LEFT) },
                        onImport = { leftPicker.launch(mimeTypes) },
                        onClear = { viewModel.clear(CompareSide.LEFT) },
                    )
                }
                item("right") {
                    SideInput(
                        title = sideTitle("Right", state.rightName),
                        text = state.right,
                        tag = "compare_right",
                        onChange = { viewModel.setText(CompareSide.RIGHT, it) },
                        onPaste = { paste(CompareSide.RIGHT) },
                        onPlayground = { viewModel.usePlaygroundCode(CompareSide.RIGHT) },
                        onImport = { rightPicker.launch(mimeTypes) },
                        onClear = { viewModel.clear(CompareSide.RIGHT) },
                    )
                }
                item("options") {
                    ToolCard(title = "Options") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = state.options.ignoreWhitespace,
                                onClick = { viewModel.setOptions(state.options.copy(ignoreWhitespace = !state.options.ignoreWhitespace)) },
                                label = { Text("Ignore whitespace") },
                                modifier = Modifier.testTag("compare_opt_whitespace"),
                            )
                            FilterChip(
                                selected = state.options.ignoreComments,
                                onClick = { viewModel.setOptions(state.options.copy(ignoreComments = !state.options.ignoreComments)) },
                                label = { Text("Ignore comments") },
                                modifier = Modifier.testTag("compare_opt_comments"),
                            )
                            FilterChip(
                                selected = state.options.ignoreLineDirectives,
                                onClick = { viewModel.setOptions(state.options.copy(ignoreLineDirectives = !state.options.ignoreLineDirectives)) },
                                label = { Text("Ignore .line") },
                                modifier = Modifier.testTag("compare_opt_line"),
                            )
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.view == CompareView.UNIFIED,
                                onClick = { viewModel.setView(CompareView.UNIFIED) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                modifier = Modifier.testTag("compare_view_unified"),
                            ) { Text("Unified") }
                            SegmentedButton(
                                selected = state.view == CompareView.SIDE_BY_SIDE,
                                onClick = { viewModel.setView(CompareView.SIDE_BY_SIDE) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                modifier = Modifier.testTag("compare_view_side"),
                            ) { Text("Side by side") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = viewModel::compare,
                                enabled = !state.running,
                                modifier = Modifier.weight(1f).testTag("compare_run"),
                            ) { Text("Compare") }
                            FilledTonalButton(onClick = viewModel::swap) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = null)
                                Text("Swap", Modifier.padding(start = 6.dp))
                            }
                        }
                        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (result != null) {
                    item("stats") { StatsCard(result, onCopy = { copyToClipboard(context, "Diff", result.toPatchText(state.leftName, state.rightName)) }) }
                    if (!result.identical) {
                        when (state.view) {
                            CompareView.UNIFIED -> items(result.unified, key = null) { line ->
                                UnifiedRow(line, colors, hScroll, codeWidth)
                            }
                            CompareView.SIDE_BY_SIDE -> items(result.sideBySide, key = null) { row ->
                                SideRowView(row, colors, hScroll, cellWidth)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideInput(
    title: String,
    text: String,
    tag: String,
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
    onPlayground: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    ToolCard(title = title) {
        CodeInputField(
            value = text,
            onValueChange = onChange,
            label = "Smali code",
            minLines = 4,
            maxLines = 8,
            singleLine = false,
            modifier = Modifier.testTag(tag),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPaste) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                Text("Paste", Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onPlayground) {
                Icon(Icons.Outlined.Code, contentDescription = null)
                Text("Use Playground code", Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onImport) {
                Icon(Icons.Outlined.FileOpen, contentDescription = null)
                Text("Import .smali", Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onClear, enabled = text.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                Text("Clear", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun StatsCard(result: DiffResult, onCopy: () -> Unit) {
    ToolCard(
        title = if (result.identical) "No differences" else "Differences",
        trailing = {
            if (!result.identical) {
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy diff as patch text") }
            }
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Stat("+${result.stats.added}", "added", AddedBg)
            Stat("−${result.stats.removed}", "removed", RemovedBg)
            Stat("~${result.stats.changed}", "changed", MaterialTheme.colorScheme.tertiary)
            Stat("${result.stats.unchanged}", "unchanged", MaterialTheme.colorScheme.outline)
        }
        if (result.approximate) {
            NoteRow("The two texts are very different, so part of the diff is shown as a full replacement instead of a minimal edit script.", warning = true)
        }
        if (result.identical) {
            Text("The compared lines are identical with the selected options.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Column(Modifier.widthIn(min = 64.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun styled(text: String, highlights: List<TextSpan>, colors: CodeColors, hlColor: Color): AnnotatedString {
    val base = SmaliHighlighter.highlight(text, colors)
    if (highlights.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        highlights.forEach { h ->
            val s = h.start.coerceIn(0, text.length)
            val e = h.end.coerceIn(s, text.length)
            if (e > s) addStyle(SpanStyle(background = hlColor, fontWeight = FontWeight.Bold), s, e)
        }
    }
}

private fun kindColor(kind: DiffKind): Color? = when (kind) {
    DiffKind.ADDED -> AddedBg
    DiffKind.REMOVED -> RemovedBg
    else -> null
}

@Composable
private fun UnifiedRow(line: UnifiedLine, colors: CodeColors, hScroll: androidx.compose.foundation.ScrollState, codeWidth: Dp) {
    val tint = kindColor(line.kind)
    val text = remember(line, colors) { styled(line.text, line.highlights, colors, (tint ?: Color.Transparent).copy(alpha = 0.45f)) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.background)
            .horizontalScroll(hScroll),
    ) {
        Row(Modifier.background((tint ?: Color.Transparent).copy(alpha = 0.18f)), verticalAlignment = Alignment.Top) {
            Gutter(line.leftLine, colors)
            Gutter(line.rightLine, colors)
            Text(
                when (line.kind) { DiffKind.ADDED -> "+"; DiffKind.REMOVED -> "−"; else -> " " },
                Modifier.width(16.dp),
                style = CodeTextStyle,
                color = tint ?: colors.gutter,
                fontWeight = FontWeight.Bold,
            )
            Text(text, Modifier.width(codeWidth), style = CodeTextStyle, color = colors.text, softWrap = false)
        }
    }
}

@Composable
private fun Gutter(number: Int?, colors: CodeColors) {
    Text(
        number?.toString() ?: "",
        Modifier.width(36.dp).padding(end = 4.dp),
        style = CodeTextStyle.copy(fontSize = CodeTextStyle.fontSize * 0.85f),
        color = colors.gutter,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}

@Composable
private fun SideRowView(row: SideRow, colors: CodeColors, hScroll: androidx.compose.foundation.ScrollState, cellWidth: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.background)
            .horizontalScroll(hScroll),
    ) {
        val leftTint = when (row.kind) { DiffKind.REMOVED, DiffKind.CHANGED -> RemovedBg; else -> null }
        val rightTint = when (row.kind) { DiffKind.ADDED, DiffKind.CHANGED -> AddedBg; else -> null }
        SideCellView(row.left, leftTint, colors, cellWidth)
        SideCellView(row.right, rightTint, colors, cellWidth)
    }
}

@Composable
private fun SideCellView(cell: SideCell?, tint: Color?, colors: CodeColors, width: Dp) {
    val text = remember(cell, colors) {
        cell?.let { styled(it.text, it.highlights, colors, (tint ?: Color.Transparent).copy(alpha = 0.45f)) } ?: AnnotatedString("")
    }
    Row(
        Modifier.width(width).background(
            when {
                cell == null -> colors.gutter.copy(alpha = 0.08f)
                tint != null -> tint.copy(alpha = 0.18f)
                else -> Color.Transparent
            },
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Gutter(cell?.lineNumber, colors)
        Text(text, Modifier.padding(start = 4.dp), style = CodeTextStyle, color = colors.text, softWrap = false)
    }
}

/** "Left" while the side has no source name, "Left: Greeter.smali" once a file or the Playground filled it. */
private fun sideTitle(side: String, name: String): String = if (name == side) side else "$side: $name"
