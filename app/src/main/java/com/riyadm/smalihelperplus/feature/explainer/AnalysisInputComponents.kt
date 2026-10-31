package com.riyadm.smalihelperplus.feature.explainer

import android.content.ClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riyadm.smalihelperplus.core.smali.SmaliAnalysis
import com.riyadm.smalihelperplus.core.smali.SmaliSamples
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity

/** Paste box + "Use Playground code" + sample chips + run button. */
@Composable
fun CodeInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onRun: () -> Unit,
    onUsePlayground: () -> Unit,
    onSample: (SmaliSamples.Sample) -> Unit,
    onClear: () -> Unit,
    running: Boolean,
    inputTag: String,
    runTag: String,
    runLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().testTag(inputTag),
                label = { Text("Smali code") },
                placeholder = { Text("Paste a method, a whole class or bare instructions such as const/4 v0, 0x1", style = MaterialTheme.typography.bodySmall) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                minLines = 5,
                maxLines = 12,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUsePlayground, modifier = Modifier.testTag("${inputTag}_use_playground")) {
                    Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Use Playground code", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrEmpty()) onValueChange(text)
                }) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Paste", Modifier.padding(start = 8.dp))
                }
                if (value.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Clear", Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text("Samples", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmaliSamples.all.forEach { s ->
                    SuggestionChip(onClick = { onSample(s) }, label = { Text(s.title) }, modifier = Modifier.testTag("${inputTag}_sample_${s.id}"))
                }
            }
            Button(onClick = onRun, enabled = !running, modifier = Modifier.fillMaxWidth().testTag(runTag)) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(runLabel, Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Chip that opens a menu to choose one of the analysed methods. */
@Composable
fun MethodPicker(methods: List<MethodDefinition>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (methods.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val current = methods.getOrNull(selected)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Method", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
        Column {
            AssistChip(
                onClick = { open = true },
                enabled = methods.size > 1,
                label = { Text(current?.let { methodLabel(it) } ?: "?", fontFamily = FontFamily.Monospace, maxLines = 1) },
                trailingIcon = if (methods.size > 1) ({ Icon(Icons.Outlined.ExpandMore, contentDescription = "Choose method") }) else null,
                modifier = Modifier.testTag("method_picker"),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                methods.forEachIndexed { i, m ->
                    DropdownMenuItem(
                        text = { Text(methodLabel(m), fontFamily = FontFamily.Monospace) },
                        onClick = { open = false; onSelect(i) },
                    )
                }
            }
        }
    }
}

fun methodLabel(m: MethodDefinition): String = if (m.isImplicit) "Snippet (${m.instructions.size} instructions)" else m.name + m.protoText

/** Compact summary of the analyser's findings, expandable to the full list. */
@Composable
fun DiagnosticsSummaryCard(analysis: SmaliAnalysis, modifier: Modifier = Modifier) {
    val relevant = analysis.diagnostics.filter { it.severity != Severity.INFO }
    if (relevant.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val errors = relevant.count { it.severity == Severity.ERROR }
    val warnings = relevant.size - errors
    Card(
        modifier.fillMaxWidth().testTag("analysis_diagnostics"),
        colors = CardDefaults.cardColors(
            containerColor = if (errors > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (errors > 0) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber, contentDescription = null)
                Text(
                    buildString {
                        if (errors > 0) append("$errors error${if (errors == 1) "" else "s"}")
                        if (errors > 0 && warnings > 0) append(" · ")
                        if (warnings > 0) append("$warnings warning${if (warnings == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
            }
            Text(
                "Results below are based on the code as written; problems may make parts of the explanation incomplete.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                relevant.take(50).forEach { d ->
                    Text("Line ${d.line}: ${d.message}" + (d.suggestion?.let { " — $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Neutral information banner. */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f).padding(start = 12.dp))
            action?.invoke()
        }
    }
}
