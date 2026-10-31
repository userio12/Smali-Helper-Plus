package com.riyadm.smalihelperplus.feature.tools.formats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.domain.tools.InstructionFormat
import com.riyadm.smalihelperplus.domain.tools.InstructionFormats
import com.riyadm.smalihelperplus.domain.tools.LayoutCell
import com.riyadm.smalihelperplus.feature.tools.common.NoteRow
import com.riyadm.smalihelperplus.feature.tools.common.ResultRow
import com.riyadm.smalihelperplus.feature.tools.common.SmaliResultLine
import com.riyadm.smalihelperplus.feature.tools.common.ToolCard
import com.riyadm.smalihelperplus.feature.tools.common.ToolPage
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute

@Composable
fun FormatExplorerScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: FormatExplorerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { InstructionFormats.byId(it) }

    BackHandler(enabled = selected != null) { selectedId = null }

    DetailScaffold(
        title = selected?.let { "Format ${it.id}" } ?: "Instruction formats",
        onBack = { if (selected != null) selectedId = null else onBack() },
    ) { padding ->
        ToolPage(padding) {
            if (selected == null) {
                ToolCard {
                    Text(
                        "Every Dalvik instruction uses one of these layouts. The id reads as: code units, maximum registers " +
                            "(r = a range), then the kind of extra data (b byte, c constant pool index, h hat, i int, l long, n nibble, " +
                            "s short, t branch target, x none).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                InstructionFormats.all.forEach { f ->
                    FormatCard(
                        f,
                        count = state.opcodesByFormat[f.id]?.size,
                        loading = state.loading,
                        modifier = Modifier.testTag("format_${f.id}"),
                    ) { selectedId = f.id }
                }
            } else {
                FormatDetail(
                    format = selected,
                    opcodes = state.opcodesByFormat[selected.id].orEmpty(),
                    loading = state.loading,
                    onOpcode = { onNavigate(OpcodeDetailRoute(it)) },
                )
            }
        }
    }
}

@Composable
private fun FormatCard(format: InstructionFormat, count: Int?, loading: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(format.id, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                if (format.optimizerOnly) Tag("optimizer only")
                if (!loading && count != null) {
                    Text("$count opcode${if (count == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
            LayoutDiagram(format)
            Text(
                format.syntax.first(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FormatDetail(format: InstructionFormat, opcodes: List<FormatOpcode>, loading: Boolean, onOpcode: (String) -> Unit) {
    ToolCard(title = format.id) {
        LayoutDiagram(format)
        Text(
            "${format.codeUnits} code unit${if (format.codeUnits == 1) "" else "s"} = ${format.byteSize} bytes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ResultRow("Layout", format.layout)
        Text("What the id means", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(format.idMeaning, style = MaterialTheme.typography.bodyMedium)
        Text(format.description, style = MaterialTheme.typography.bodyMedium)
        if (format.optimizerOnly) {
            NoteRow("Optimizer / verifier format: it is not valid in regular DEX files.", warning = true)
        }
    }

    ToolCard(title = if (format.syntax.size == 1) "Syntax" else "Syntax variants") {
        format.syntax.forEach { ResultRow("", it) }
        format.notes.forEach { NoteRow(it) }
    }

    if (format.examples.isNotEmpty()) {
        ToolCard(title = "Smali examples") {
            format.examples.forEach { SmaliResultLine(it) }
        }
    }

    ToolCard(title = "Opcodes using ${format.id}") {
        when {
            loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            opcodes.isEmpty() -> Text(
                if (format.optimizerOnly) "No opcode in the regular instruction set uses this format." else "No opcode in the database uses this format.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> opcodes.forEachIndexed { i, op ->
                if (i > 0) HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { onOpcode(op.name) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%02x".format(op.opcode and 0xff), Modifier.width(36.dp), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(op.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text(op.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Open ${op.name}")
                }
            }
        }
    }
}

/** One row of boxes per 16-bit code unit; box width is proportional to the field's bit width. */
@Composable
fun LayoutDiagram(format: InstructionFormat, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        format.words.forEachIndexed { index, cells ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}",
                    Modifier.width(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    cells.forEach { cell -> CellBox(cell, Modifier.weight(cell.bits.toFloat())) }
                }
            }
        }
    }
}

@Composable
private fun CellBox(cell: LayoutCell, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        cell.isOpcode -> scheme.primary to scheme.onPrimary
        cell.isZero -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Box(
        modifier
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = bg,
            contentColor = fg,
            border = BorderStroke(1.dp, Color.Transparent),
            modifier = Modifier.fillMaxWidth().height(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    cell.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                )
            }
        }
    }
}
