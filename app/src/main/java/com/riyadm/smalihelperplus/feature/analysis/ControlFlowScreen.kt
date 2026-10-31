package com.riyadm.smalihelperplus.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliHighlighter
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeTextStyle
import com.riyadm.smalihelperplus.core.designsystem.theme.LocalCodeColors
import com.riyadm.smalihelperplus.core.smali.analysis.BasicBlock
import com.riyadm.smalihelperplus.core.smali.analysis.CfgEdge
import com.riyadm.smalihelperplus.core.smali.analysis.ControlFlowGraph
import com.riyadm.smalihelperplus.core.smali.analysis.EdgeKind
import com.riyadm.smalihelperplus.feature.explainer.CodeInputCard
import com.riyadm.smalihelperplus.feature.explainer.DiagnosticsSummaryCard
import com.riyadm.smalihelperplus.feature.explainer.InfoBanner
import com.riyadm.smalihelperplus.feature.explainer.MethodPicker
import com.riyadm.smalihelperplus.feature.explainer.SnippetAnalysisViewModel
import com.riyadm.smalihelperplus.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ControlFlowViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    codeHandoff: CodeHandoff,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : SnippetAnalysisViewModel(preferences, codeHandoff, CodeHandoff.CONTROL_FLOW, dispatcher)

/** "true → B3", "case 1 → B5", "catch Ljava/io/IOException; → B7". */
fun edgeLabel(edge: CfgEdge): String = "${edge.label} → B${edge.to}"

@Composable
fun ControlFlowScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: ControlFlowViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.consumeHandoff() }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    DetailScaffold(
        title = "Control flow",
        onBack = onBack,
        actions = {
            if (state.insight != null) {
                IconButton(onClick = { zoom = (zoom - 0.15f).coerceAtLeast(0.7f) }, modifier = Modifier.testTag("cfg_zoom_out")) {
                    Icon(Icons.Outlined.ZoomOut, contentDescription = "Smaller code")
                }
                IconButton(onClick = { zoom = (zoom + 0.15f).coerceAtMost(1.75f) }, modifier = Modifier.testTag("cfg_zoom_in")) {
                    Icon(Icons.Outlined.ZoomIn, contentDescription = "Larger code")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("cfg_list"),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var index = 0
            item { CodeInputCard(
                value = viewModel.input,
                onValueChange = viewModel::onInputChange,
                onRun = { viewModel.run("Pasted code") },
                onUsePlayground = viewModel::usePlaygroundCode,
                onSample = viewModel::loadSample,
                onClear = viewModel::clear,
                running = state.running,
                inputTag = "cfg_input",
                runTag = "cfg_run",
                runLabel = "Build control flow graph",
            ) }
            index++
            state.message?.let { msg ->
                item { InfoBanner(msg, action = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } }) }
                index++
            }
            val insight = state.insight
            if (insight == null) {
                if (state.message == null && !state.running) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.AccountTree,
                            title = "Visualise control flow",
                            message = "Split a method into basic blocks and see every branch, switch case and exception edge.",
                        )
                    }
                }
                return@LazyColumn
            }
            state.analysis?.let { a -> item { DiagnosticsSummaryCard(a) }; index++ }
            item { MethodPicker(state.methods, state.selectedMethod, viewModel::selectMethod) }
            index++
            val cfg = insight.controlFlow
            item {
                Text(
                    "${cfg.blocks.size} basic block${if (cfg.blocks.size == 1) "" else "s"} · ${cfg.edges.size} edge${if (cfg.edges.size == 1) "" else "s"}. Tap an edge to jump to its target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            index++
            if (cfg.notes.isNotEmpty()) {
                item { InfoBanner(cfg.notes.joinToString("\n")) }
                index++
            }
            val firstBlockIndex = index
            cfg.blocks.forEach { block ->
                item(key = "block_${block.id}") {
                    BlockCard(
                        cfg = cfg,
                        block = block,
                        zoom = zoom,
                        onEdge = { target -> scope.launch { listState.animateScrollToItem(firstBlockIndex + target) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockCard(cfg: ControlFlowGraph, block: BasicBlock, zoom: Float, onEdge: (Int) -> Unit) {
    val colors = LocalCodeColors.current
    val code = remember(block) {
        buildString {
            block.labels.forEach { append(':').append(it).append('\n') }
            append(block.instructions.joinToString("\n") { it.text })
        }
    }
    val highlighted = remember(code, colors) { SmaliHighlighter.highlight(code, colors) }
    val outgoing = cfg.outgoing(block.id)
    val incoming = cfg.incoming(block.id)
    Column {
        Card(
            Modifier.fillMaxWidth().testTag("cfg_block"),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    block.isHandler -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    block.isEntry -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(block.name, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    if (block.isEntry) Tag("entry")
                    if (block.isHandler) Tag("catch handler")
                    val first = block.instructions.first().line
                    val last = block.instructions.last().line
                    Text(if (first == last) "line $first" else "lines $first–$last", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.background)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(highlighted, style = CodeTextStyle.copy(fontSize = 13.sp * zoom, lineHeight = 19.sp * zoom), color = colors.text, softWrap = false)
                }
                if (incoming.isNotEmpty()) {
                    Text(
                        "From: " + incoming.joinToString(", ") { "B${it.from} (${it.label})" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (outgoing.isEmpty()) {
                    val flow = block.instructions.last().opcode?.flow?.name?.lowercase()
                    Text(
                        when (flow) {
                            "return" -> "Exit: returns from the method"
                            "throw" -> "Exit: throws (to a handler only if covered by a try range)"
                            else -> "No outgoing edges"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        outgoing.forEach { e ->
                            val container = edgeColor(e.kind)
                            AssistChip(
                                onClick = { onEdge(e.to) },
                                label = { Text(edgeLabel(e), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = container,
                                    labelColor = MaterialTheme.colorScheme.contentColorFor(container),
                                ),
                                modifier = Modifier.testTag("cfg_edge"),
                            )
                        }
                    }
                }
            }
        }
        // Visual connector when control falls through into the next block.
        val falls = outgoing.firstOrNull { it.kind == EdgeKind.FALLTHROUGH && it.to == block.id + 1 }
        if (falls != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                Text(falls.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun edgeColor(kind: EdgeKind): Color = when (kind) {
    EdgeKind.BRANCH_TAKEN -> MaterialTheme.colorScheme.tertiaryContainer
    EdgeKind.GOTO -> MaterialTheme.colorScheme.secondaryContainer
    EdgeKind.SWITCH_CASE -> MaterialTheme.colorScheme.primaryContainer
    EdgeKind.EXCEPTION -> MaterialTheme.colorScheme.errorContainer
    EdgeKind.FALLTHROUGH -> MaterialTheme.colorScheme.surfaceContainerHighest
}
