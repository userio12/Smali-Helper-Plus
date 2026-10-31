package com.riyadm.smalihelperplus.feature.tools.compatibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.domain.tools.AndroidVersions
import com.riyadm.smalihelperplus.domain.tools.CompatReport
import com.riyadm.smalihelperplus.domain.tools.OpcodeCompat
import com.riyadm.smalihelperplus.feature.tools.common.CodeInputField
import com.riyadm.smalihelperplus.feature.tools.common.NoteRow
import com.riyadm.smalihelperplus.feature.tools.common.PresetChips
import com.riyadm.smalihelperplus.feature.tools.common.ResultRow
import com.riyadm.smalihelperplus.feature.tools.common.ToolCard
import com.riyadm.smalihelperplus.feature.tools.common.ToolPage
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute

private const val SAMPLE = """.method public static call(Ljava/lang/invoke/MethodHandle;)V
    .locals 2

    const-string v0, "x"

    invoke-polymorphic {p0, v0}, Ljava/lang/invoke/MethodHandle;->invoke([Ljava/lang/Object;)Ljava/lang/Object;, (Ljava/lang/String;)V

    return-void
.end method"""

@Composable
fun CompatibilityCheckerScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: CompatibilityCheckerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(title = "Compatibility checker", onBack = onBack) { padding ->
        ToolPage(padding) {
            ToolCard(title = "Target") {
                MinSdkDropdown(state.minSdk, viewModel::setMinSdk)
                CodeInputField(
                    value = state.code,
                    onValueChange = viewModel::setCode,
                    label = "Smali code",
                    placeholder = "Paste a method or a whole class",
                    minLines = 6,
                    maxLines = 12,
                    singleLine = false,
                    supportingText = "Comments, directives, labels and switch/array data are ignored.",
                    modifier = Modifier.testTag("compat_code"),
                )
                PresetChips(listOf("Sample with invoke-polymorphic" to SAMPLE), onPick = viewModel::setCode)
            }

            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            val report = state.report
            if (report != null) {
                ReportCards(report, onOpen = { onNavigate(OpcodeDetailRoute(it)) })
            } else if (!state.loading) {
                ToolCard {
                    Text(
                        "Paste smali to find instructions that need an API level above the one you target.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            NewerOpcodesCard(state.newerOpcodes, state.opcodeCount, onOpen = { onNavigate(OpcodeDetailRoute(it)) })
        }
    }
}

@Composable
private fun MinSdkDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = AndroidVersions.label(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Minimum API level (minSdk)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("compat_api"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AndroidVersions.all.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.label) },
                    onClick = { onSelect(v.api); expanded = false },
                    modifier = Modifier.testTag("compat_api_${v.api}"),
                )
            }
        }
    }
}

@Composable
private fun ReportCards(report: CompatReport, onOpen: (String) -> Unit) {
    ToolCard(title = "Summary") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (report.compatible) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (report.compatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                if (report.compatible) "Compatible with ${AndroidVersions.label(report.targetMinSdk)}"
                else "${report.issues.size} instruction${if (report.issues.size == 1) "" else "s"} need a newer API than ${report.targetMinSdk}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        ResultRow("Instructions found", report.instructionCount.toString(), mono = false)
        ResultRow("Lowest API that runs this code", AndroidVersions.label(report.requiredMinApi), mono = false)
        val dexApi = AndroidVersions.apiForDexVersion(report.requiredDexVersion)
        ResultRow(
            "DEX version required",
            "${report.requiredDexVersion}" + (dexApi?.let { " (Android ${AndroidVersions.byApi(it)?.release ?: ""}, API $it and up)" } ?: ""),
            mono = false,
        )
        if (report.unknown.isNotEmpty()) {
            NoteRow("${report.unknown.size} line${if (report.unknown.size == 1) "" else "s"} start with a mnemonic that is not in the opcode database.", warning = true)
        }
    }

    if (report.issues.isNotEmpty()) {
        ToolCard(title = "Too new for API ${report.targetMinSdk}") {
            report.issues.forEachIndexed { i, issue ->
                if (i > 0) HorizontalDivider()
                Column(
                    Modifier.fillMaxWidth().clickable { onOpen(issue.mnemonic) }.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(issue.mnemonic, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("line ${issue.line}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Needs ${AndroidVersions.label(issue.requiredApi)}, DEX ${issue.dexVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        issue.lineText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (report.unknown.isNotEmpty()) {
        ToolCard(title = "Unknown mnemonics") {
            report.unknown.take(50).forEachIndexed { i, u ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.mnemonic, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Text("line ${u.line}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (report.unknown.size > 50) {
                Text("… and ${report.unknown.size - 50} more", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NewerOpcodesCard(opcodes: List<OpcodeCompat>, total: Int, onOpen: (String) -> Unit) {
    ToolCard(title = "Opcodes that need more than API 1") {
        if (total == 0) {
            Text(
                "The opcode database is empty or still loading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (opcodes.isEmpty()) {
            Text("Every opcode in the database is available from API 1.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row {
                Text("Opcode", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("API", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                Text("DEX", Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            opcodes.forEach { op ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(op.name) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(op.name, Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Text(op.minApi.toString(), Modifier.width(64.dp), fontFamily = FontFamily.Monospace)
                    Text(op.dexVersion, Modifier.width(48.dp), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
