package com.riyadm.smalihelperplus.feature.explainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.smali.MethodInsight
import com.riyadm.smalihelperplus.core.smali.explain.PseudocodeGenerator
import com.riyadm.smalihelperplus.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@HiltViewModel
class SmaliExplainerViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    codeHandoff: CodeHandoff,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : SnippetAnalysisViewModel(preferences, codeHandoff, CodeHandoff.EXPLAINER, dispatcher)

private val TABS = listOf("Step-by-step", "Pseudocode", "Registers")

@Composable
fun SmaliExplainerScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: SmaliExplainerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { viewModel.consumeHandoff() }

    DetailScaffold(title = "Smali explainer", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("explainer_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    inputTag = "explainer_input",
                    runTag = "explainer_run",
                    runLabel = "Explain",
                )
            }
            state.message?.let { msg -> item { InfoBanner(msg, action = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } }) } }
            val insight = state.insight
            if (insight == null) {
                if (state.message == null && !state.running) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Lightbulb,
                            title = "Explain smali step by step",
                            message = "Paste code, use the Playground buffer or pick a sample, then tap Explain. Everything is analysed on the device; nothing is executed.",
                        )
                    }
                }
                return@LazyColumn
            }
            state.analysis?.let { a -> item { DiagnosticsSummaryCard(a) } }
            item { MethodPicker(state.methods, state.selectedMethod, viewModel::selectMethod) }
            item {
                PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.testTag("explainer_tabs")) {
                    TABS.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }, modifier = Modifier.testTag("explainer_tab_$i"))
                    }
                }
            }
            when (tab) {
                0 -> stepItems(insight)
                1 -> pseudocodeItems(insight)
                else -> registerItems(insight)
            }
        }
    }
}

private fun LazyListScope.stepItems(insight: MethodInsight) {
    items(insight.steps, key = { "step_${it.index}" }) { step ->
        Card(Modifier.fillMaxWidth().testTag("explainer_step"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "#${step.index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text("Line ${step.line}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
                CodeBlock(code = step.code, showCopy = false)
                Text(step.explanation, style = MaterialTheme.typography.bodyMedium)
                step.notes.forEach { n ->
                    Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun LazyListScope.pseudocodeItems(insight: MethodInsight) {
    item {
        InfoBanner(
            "${PseudocodeGenerator.DISCLAIMER}. It is generated from the instructions for learning purposes; loops stay as goto, and anything not understood is marked /* untranslated */.",
            modifier = Modifier.testTag("explainer_pseudocode_disclaimer"),
        )
    }
    item { CodeBlock(code = insight.pseudocode, language = "java", label = "Pseudocode", modifier = Modifier.testTag("explainer_pseudocode")) }
}

private fun LazyListScope.registerItems(insight: MethodInsight) {
    val r = insight.registers
    val slots = r.frame.parameterSlots
    if (slots.isNotEmpty()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Parameters", style = MaterialTheme.typography.titleSmall)
                    slots.forEach { s ->
                        Text(
                            "${s.pRegister}${s.vRegister?.let { " = $it" } ?: ""}  ${if (s.isThis) "this" else s.javaType + (s.name?.let { " $it" } ?: "") + if (s.isHighWord) " (high word)" else ""}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
    item {
        Text(
            r.frame.size?.let { "Frame: $it registers (${r.frame.locals ?: "?"} locals + ${r.frame.parameterWords ?: "?"} parameter words)" }
                ?: "Frame size unknown (snippet without .registers/.locals); registers are listed as used.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (r.registers.isEmpty()) {
        item { Text("No registers are used.", style = MaterialTheme.typography.bodyMedium) }
        return
    }
    items(r.registers, key = { "reg_$it" }) { key ->
        val s = r.summaries.getValue(key)
        val writes = r.usages.filter { key in it.writes }
        val reads = r.usages.filter { key in it.reads }
        Card(Modifier.fillMaxWidth().testTag("explainer_register_row"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.display, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
                    val role = s.parameter?.let { p -> if (p.isThis) "this" else "parameter ${p.javaType}${p.name?.let { " $it" } ?: ""}" } ?: "local"
                    Text(
                        role + if (s.usedAsWide) " · 64-bit pair" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (writes.isEmpty()) {
                    Text(if (s.liveAtEntry) "Holds its incoming value (never assigned here)" else "Never assigned", style = MaterialTheme.typography.bodySmall)
                } else {
                    writes.forEach { u ->
                        Text("assigned at line ${u.instruction.line}: ${u.instruction.text}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    if (reads.isEmpty()) "Never read" else "read at line${if (reads.size == 1) "" else "s"} ${reads.joinToString(", ") { it.instruction.line.toString() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
