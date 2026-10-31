package com.riyadm.smalihelperplus.feature.analysis

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.model.Descriptors
import com.riyadm.smalihelperplus.core.smali.structure.MethodOutline
import com.riyadm.smalihelperplus.core.smali.structure.StructureAnalyzer
import com.riyadm.smalihelperplus.feature.explainer.InfoBanner
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.ControlFlowRoute
import com.riyadm.smalihelperplus.navigation.PlaygroundRoute
import com.riyadm.smalihelperplus.navigation.RegisterVisualizerRoute
import com.riyadm.smalihelperplus.navigation.SmaliExplainerRoute

private data class SelectedMethod(val sourceId: Long, val methodIndex: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MethodExplorerScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: MethodExplorerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.consumeHandoff() }
    var selected by remember { mutableStateOf<SelectedMethod?>(null) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.openDocuments(uris)
    }

    DetailScaffold(
        title = "Method explorer",
        onBack = onBack,
        actions = {
            IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("method_explorer_open")) {
                Icon(Icons.Outlined.FileOpen, contentDescription = "Open .smali files")
            }
            IconButton(onClick = viewModel::usePlaygroundCode, modifier = Modifier.testTag("method_explorer_playground")) {
                Icon(Icons.Outlined.Code, contentDescription = "Use Playground code")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("method_explorer_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.message?.let { msg ->
                item { InfoBanner(msg, action = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } }) }
            }
            if (state.sources.isEmpty()) {
                if (!state.loading) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Schema,
                            title = "Explore classes and methods",
                            message = "Open one or more .smali files, or use the Playground code, to list fields and methods with readable Java-style signatures.",
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
                            FilledTonalButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("method_explorer_open_empty")) { Text("Open files") }
                            OutlinedButton(onClick = viewModel::usePlaygroundCode) { Text("Use Playground code") }
                        }
                    }
                }
                return@LazyColumn
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().testTag("method_explorer_search"),
                    placeholder = { Text("Filter methods and fields") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (state.query.isNotEmpty()) ({
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear filter") }
                    }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sources.forEach { s ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(s.name) },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.removeSource(s.id) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove ${s.name}") }
                            },
                        )
                    }
                }
            }
            val q = state.query.trim()
            state.sources.forEach { source ->
                val outline = source.analysis.outline
                val methods = source.analysis.file.methods
                val outlines = methods.map { StructureAnalyzer.methodOutline(it, outline.simpleName) }
                val methodMatches = methods.indices.filter { i ->
                    q.isEmpty() || outlines[i].javaSignature.contains(q, true) || methods[i].signature.contains(q, true)
                }
                val fieldMatches = outline.fields.filter { q.isEmpty() || it.javaDeclaration.contains(q, true) || it.descriptor.contains(q, true) }
                item(key = "class_${source.id}") {
                    Card(Modifier.fillMaxWidth().testTag("method_explorer_class"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(source.name, style = MaterialTheme.typography.labelMedium)
                            if (outline.descriptor != null) {
                                Text(outline.javaHeader, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                                Text(outline.javaName ?: "", style = MaterialTheme.typography.bodySmall)
                                outline.superClass?.let { Text("Superclass: ${Descriptors.toJavaName(it)}", style = MaterialTheme.typography.bodySmall) }
                                if (outline.interfaces.isNotEmpty()) {
                                    Text("Interfaces: ${outline.interfaces.joinToString(", ") { Descriptors.toJavaName(it) }}", style = MaterialTheme.typography.bodySmall)
                                }
                                outline.source?.let { Text("Source file: $it", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                Text("No .class directive (code snippet)", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "${outline.fields.size} field${if (outline.fields.size == 1) "" else "s"} · ${methods.size} method${if (methods.size == 1) "" else "s"} · ${source.analysis.errorCount} error${if (source.analysis.errorCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (fieldMatches.isNotEmpty()) {
                    item(key = "fields_${source.id}") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Fields", style = MaterialTheme.typography.titleSmall)
                                fieldMatches.forEach { f ->
                                    Text(f.javaDeclaration, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                if (methodMatches.isEmpty() && q.isNotEmpty()) {
                    item(key = "nomatch_${source.id}") { Text("No methods match \"$q\".", style = MaterialTheme.typography.bodySmall) }
                }
                methodMatches.forEach { i ->
                    item(key = "m_${source.id}_$i") {
                        MethodCard(outlines[i], methods[i]) { selected = SelectedMethod(source.id, i) }
                    }
                }
            }
        }
    }

    val sel = selected
    val source = sel?.let { s -> state.sources.firstOrNull { it.id == s.sourceId } }
    val method = source?.analysis?.file?.methods?.getOrNull(sel.methodIndex)
    if (source != null && method != null) {
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            MethodDetail(
                outline = StructureAnalyzer.methodOutline(method, source.analysis.outline.simpleName),
                body = StructureAnalyzer.methodSource(source.text, method),
                onSend = { target, route ->
                    viewModel.offerMethod(source, method, target)
                    selected = null
                    onNavigate(route)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodCard(outline: MethodOutline, method: MethodDefinition, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("method_explorer_method"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(outline.javaSignature, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Text(method.signature, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${outline.instructionCount} instructions", style = MaterialTheme.typography.labelMedium)
                outline.registers?.let { Text("$it registers", style = MaterialTheme.typography.labelMedium) }
                outline.locals?.let { Text("$it locals", style = MaterialTheme.typography.labelMedium) }
                Text("line ${outline.startLine}", style = MaterialTheme.typography.labelMedium)
            }
            if (outline.flags.isNotEmpty()) {
                Text(outline.flags.joinToString(" "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MethodDetail(outline: MethodOutline, body: String, onSend: (String, AppRoute) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .testTag("method_explorer_detail"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(outline.javaSignature, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
        Text(outline.smaliSignature, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Text(
            buildString {
                append("Returns ${outline.javaReturnType ?: "?"}")
                append(" · ${outline.instructionCount} instructions")
                outline.registers?.let { append(" · $it registers") }
                outline.parameterWords?.let { append(" · $it parameter words") }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (outline.parameters.isNotEmpty()) {
            Text("Parameters", style = MaterialTheme.typography.titleSmall)
            outline.parameters.forEachIndexed { i, p ->
                Text("${i + 1}. ${p.javaType}${p.name?.let { " $it" } ?: ""}  (${p.descriptor})", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onSend(CodeHandoff.PLAYGROUND, PlaygroundRoute) }, modifier = Modifier.testTag("method_explorer_open_playground")) {
                Icon(Icons.Outlined.Code, contentDescription = null)
                Text("Open in Playground", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { onSend(CodeHandoff.EXPLAINER, SmaliExplainerRoute) }) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null)
                Text("Explain", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { onSend(CodeHandoff.CONTROL_FLOW, ControlFlowRoute) }) {
                Icon(Icons.Outlined.AccountTree, contentDescription = null)
                Text("Control flow", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { onSend(CodeHandoff.REGISTER_VISUALIZER, RegisterVisualizerRoute) }) {
                Icon(Icons.Outlined.GridView, contentDescription = null)
                Text("Registers", Modifier.padding(start = 8.dp))
            }
        }
        CodeBlock(code = body, label = "Method body", modifier = Modifier.padding(bottom = 16.dp))
    }
}
