package com.riyadm.smalihelperplus.feature.tools.cheatsheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.domain.tools.CheatBlock
import com.riyadm.smalihelperplus.domain.tools.CheatSheets
import com.riyadm.smalihelperplus.domain.tools.CheatTable
import com.riyadm.smalihelperplus.feature.tools.common.CopyIconButton
import com.riyadm.smalihelperplus.feature.tools.common.ToolCard
import com.riyadm.smalihelperplus.feature.tools.common.ToolPage
import com.riyadm.smalihelperplus.navigation.AppRoute

@Composable
fun CheatSheetsScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sheets = CheatSheets.all
    val sheet = sheets[tab.coerceIn(0, sheets.lastIndex)]

    DetailScaffold(
        title = "Cheat sheets",
        onBack = onBack,
        actions = { CopyIconButton(sheet.copyText, "${sheet.title} sheet") },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab.coerceIn(0, sheets.lastIndex), edgePadding = 8.dp) {
                sheets.forEachIndexed { i, s ->
                    Tab(
                        selected = i == tab,
                        onClick = { tab = i },
                        text = { Text(s.title, maxLines = 1) },
                        modifier = Modifier.testTag("cheat_tab_${s.id}"),
                    )
                }
            }
            ToolPage(PaddingValuesZero) {
                Text(sheet.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sheet.blocks.forEach { BlockCard(it) }
            }
        }
    }
}

private val PaddingValuesZero = androidx.compose.foundation.layout.PaddingValues(0.dp)

@Composable
private fun BlockCard(block: CheatBlock) {
    ToolCard(title = block.title, trailing = { CopyIconButton(block.copyText, block.title) }) {
        block.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        block.table?.let { TableView(it) }
        block.code?.let { CodeBlock(it, showCopy = true, label = "smali") }
    }
}

@Composable
private fun TableView(table: CheatTable) {
    // Size the key column to its longest entry (capped) so short codes like "Z" leave room for the other columns.
    val measurer = rememberTextMeasurer()
    val keyStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    val headerStyle = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val firstColumn = remember(table, keyStyle, headerStyle, density) {
        val widest = table.rows.mapNotNull { it.firstOrNull() }.maxOfOrNull { measurer.measure(it, keyStyle).size.width } ?: 0
        val header = table.headers.firstOrNull()?.let { measurer.measure(it, headerStyle).size.width } ?: 0
        with(density) { (maxOf(widest, header).toDp() + 8.dp).coerceAtMost(FIRST_COLUMN) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            table.headers.forEachIndexed { i, h ->
                Text(
                    h,
                    modifier = if (i == 0) Modifier.width(firstColumn) else Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        table.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                row.forEachIndexed { i, cell ->
                    Text(
                        cell,
                        modifier = if (i == 0) Modifier.width(firstColumn) else Modifier.weight(1f).padding(start = 8.dp),
                        style = if (i == 0) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodySmall,
                        fontFamily = if (i == 0) FontFamily.Monospace else null,
                        fontWeight = if (i == 0) FontWeight.SemiBold else null,
                    )
                }
            }
            if (r < table.rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

/** Upper bound for the key column; longer keys such as class descriptors wrap inside it. */
private val FIRST_COLUMN = 132.dp
