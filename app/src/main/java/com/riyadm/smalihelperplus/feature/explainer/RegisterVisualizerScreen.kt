package com.riyadm.smalihelperplus.feature.explainer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.smali.analysis.RegKey
import com.riyadm.smalihelperplus.core.smali.analysis.RegisterAnalysis
import com.riyadm.smalihelperplus.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class RegisterVisualizerViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    codeHandoff: CodeHandoff,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : SnippetAnalysisViewModel(preferences, codeHandoff, CodeHandoff.REGISTER_VISUALIZER, dispatcher)

private val CELL: Dp = 44.dp
private val CODE_COLUMN: Dp = 172.dp

@Composable
fun RegisterVisualizerScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: RegisterVisualizerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.consumeHandoff() }
    val gridScroll = rememberScrollState()

    DetailScaffold(title = "Register usage", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("registers_list"),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CodeInputCard(
                    value = viewModel.input,
                    onValueChange = viewModel::onInputChange,
                    onRun = { viewModel.run("Pasted code") },
                    onUsePlayground = viewModel::usePlaygroundCode,
                    onSample = viewModel::loadSample,
                    onClear = viewModel::clear,
                    running = state.running,
                    inputTag = "registers_input",
                    runTag = "registers_run",
                    runLabel = "Visualise registers",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            state.message?.let { msg ->
                item { InfoBanner(msg, Modifier.padding(horizontal = 16.dp), action = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } }) }
            }
            val insight = state.insight
            if (insight == null) {
                if (state.message == null && !state.running) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.GridView,
                            title = "See how registers are used",
                            message = "A grid shows which instruction reads (R) or writes (W) each register, 64-bit pairs and where values are live.",
                        )
                    }
                }
                return@LazyColumn
            }
            state.analysis?.let { a -> item { DiagnosticsSummaryCard(a, Modifier.padding(horizontal = 16.dp)) } }
            item { MethodPicker(state.methods, state.selectedMethod, viewModel::selectMethod, Modifier.padding(horizontal = 16.dp)) }
            val r = insight.registers
            item { ParameterMappingCard(r, Modifier.padding(horizontal = 16.dp)) }
            item { Legend(Modifier.padding(horizontal = 16.dp)) }
            if (r.registers.isEmpty()) {
                item { Text("No registers are used by these instructions.", Modifier.padding(16.dp)) }
                return@LazyColumn
            }
            item { GridHeader(r, gridScroll) }
            itemsIndexed(r.usages, key = { i, _ -> "row_$i" }) { i, _ -> GridRow(r, i, gridScroll) }
            item {
                Text(
                    "Register summary",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
            }
            r.registers.forEach { key ->
                item(key = "sum_$key") { SummaryCard(r, key, Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }
}

@Composable
private fun ParameterMappingCard(r: RegisterAnalysis, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().testTag("registers_params"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Register frame", style = MaterialTheme.typography.titleSmall)
            val f = r.frame
            Text(
                if (f.isKnown) "${f.size} registers: v0–v${(f.locals ?: 1) - 1} are locals, the last ${f.parameterWords} hold the arguments."
                else "Frame size unknown: this is a snippet without .registers/.locals, so pN registers are shown separately.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (f.isKnown && f.locals == 0) {
                Text("This method has no local registers.", style = MaterialTheme.typography.bodySmall)
            }
            f.parameterSlots.forEach { s ->
                Text(
                    "${s.pRegister} ↔ ${s.vRegister ?: "?"}   " + when {
                        s.isThis -> "this (${s.javaType})"
                        s.isHighWord -> "high word of ${s.javaType}${s.name?.let { " $it" } ?: ""}"
                        else -> "${s.javaType}${s.name?.let { " $it" } ?: ""}"
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("W", "write", MaterialTheme.colorScheme.tertiaryContainer)
        LegendItem("R", "read", MaterialTheme.colorScheme.secondaryContainer)
        LegendItem("", "live value", MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        LegendItem("²", "64-bit pair", MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

@Composable
private fun LegendItem(symbol: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.labelSmall)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun GridHeader(r: RegisterAnalysis, scroll: ScrollState) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Instruction", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(CODE_COLUMN))
            Row(Modifier.horizontalScroll(scroll)) {
                r.registers.forEach { key ->
                    val label = r.summaries[key]?.display ?: key.toString()
                    Text(
                        label.replace(" (", "\n("),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(CELL),
                    )
                }
            }
        }
    }
}

@Composable
private fun GridRow(r: RegisterAnalysis, index: Int, scroll: ScrollState) {
    val usage = r.usages[index]
    val live = r.live[index]
    val liveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val writeColor = MaterialTheme.colorScheme.tertiaryContainer
    val readColor = MaterialTheme.colorScheme.secondaryContainer
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("registers_grid_row"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(CODE_COLUMN).padding(end = 8.dp)) {
            Text("${index + 1} · line ${usage.instruction.line}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(usage.instruction.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.horizontalScroll(scroll)) {
            r.registers.forEach { key ->
                val w = key in usage.writes
                val rd = key in usage.reads
                val isLive = key in live
                val wide = key in usage.wide
                val bg = when {
                    w -> writeColor
                    rd -> readColor
                    isLive -> liveColor
                    else -> Color.Transparent
                }
                val label = buildString {
                    if (rd && w) append("RW") else if (w) append("W") else if (rd) append("R")
                    if (wide && (rd || w)) append("²")
                }
                val description = buildString {
                    append(r.summaries[key]?.display ?: key.toString())
                    append(
                        when {
                            rd && w -> ": read and written"
                            w -> ": written"
                            rd -> ": read"
                            isLive -> ": holds a live value"
                            else -> ": unused"
                        },
                    )
                    if (wide && (rd || w)) append(" (64-bit pair)")
                }
                Box(
                    Modifier
                        .size(CELL, 36.dp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(r: RegisterAnalysis, key: RegKey, modifier: Modifier = Modifier) {
    val s = r.summaries.getValue(key)
    fun line(i: Int?) = i?.let { "line ${r.usages[it].instruction.line}" } ?: "—"
    Card(modifier.fillMaxWidth().testTag("registers_summary"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.display, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                val role = s.parameter?.let { if (it.isThis) "this" else "parameter" + (it.name?.let { n -> " $n" } ?: "") } ?: "local"
                Text(role + if (s.usedAsWide) " · 64-bit" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("First definition: ${line(s.firstDefinition)} · Last use: ${line(s.lastUse)}", style = MaterialTheme.typography.bodySmall)
            Text("${s.writes} write${if (s.writes == 1) "" else "s"}, ${s.reads} read${if (s.reads == 1) "" else "s"}" + if (s.liveAtEntry) " · live at entry" else "", style = MaterialTheme.typography.bodySmall)
            val ranges = s.liveRanges.joinToString(", ") { range ->
                val a = r.usages[range.first].instruction.line
                val b = r.usages[range.last].instruction.line
                if (a == b) "line $a" else "lines $a–$b"
            }
            Text(if (ranges.isEmpty()) "Never holds a value that is used later" else "Live: $ranges", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
