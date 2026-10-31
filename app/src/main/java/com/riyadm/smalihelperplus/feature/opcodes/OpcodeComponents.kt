package com.riyadm.smalihelperplus.feature.opcodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.domain.search.MatchSnippet
import com.riyadm.smalihelperplus.domain.search.SearchResult
import com.riyadm.smalihelperplus.feature.search.rememberHighlighted

/** Small rounded label (format id, hex value, API level). */
@Composable
fun SmallTag(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    monospace: Boolean = false,
) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            maxLines = 1,
        )
    }
}

/**
 * One opcode in a list or in search results. [result] carries optional highlight ranges and a match snippet; when it is
 * a plain list entry both are empty.
 */
@Composable
fun OpcodeRow(
    result: SearchResult,
    bookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showMatchLabel: Boolean = false,
) {
    val opcode = result.opcode
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Name and hex tag share one weighted slot so the name can use all free width (a second weighted
                // spacer would cap it at half the row and ellipsize names like move-wide/from16).
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rememberHighlighted(opcode.name, result.nameRanges),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    SmallTag(
                        opcode.hexValue,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        monospace = true,
                    )
                }
                if (bookmarked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Bookmarked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                rememberHighlighted(opcode.summary, result.summaryRanges),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val snippet = result.snippet
            if (showMatchLabel && snippet != null) MatchSnippetLine(snippet)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallTag("Format ${opcode.format}", monospace = true)
                if (opcode.minApi > 1) {
                    SmallTag(
                        "API ${opcode.minApi}+",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

/** "in example: const/4 v0, 0x1" line explaining a match outside name and summary. */
@Composable
private fun MatchSnippetLine(snippet: MatchSnippet) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Matched in ${snippet.field.label}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            rememberHighlighted(snippet.text, snippet.ranges),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A plain list entry (no highlights). */
fun Opcode.asListResult(): SearchResult = SearchResult(
    opcode = this,
    score = 0,
    matchedField = com.riyadm.smalihelperplus.domain.search.MatchField.NAME,
    nameRanges = emptyList(),
    summaryRanges = emptyList(),
    snippet = null,
)

fun Modifier.opcodeItemTag(name: String): Modifier = this.testTag("opcode_item_$name")
