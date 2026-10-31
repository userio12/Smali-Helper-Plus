package com.riyadm.smalihelperplus.feature.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/** Builds an [AnnotatedString] with [ranges] (inclusive character indices) highlighted. Out-of-bounds ranges are clamped. */
fun highlightedText(text: String, ranges: List<IntRange>, background: Color, foreground: Color): AnnotatedString {
    if (ranges.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        val style = SpanStyle(background = background, color = foreground, fontWeight = FontWeight.SemiBold)
        for (r in ranges) {
            val start = r.first.coerceIn(0, text.length)
            val end = (r.last + 1).coerceIn(0, text.length)
            if (end > start) addStyle(style, start, end)
        }
    }
}

/** Highlights search matches with the primary container color. */
@Composable
fun rememberHighlighted(text: String, ranges: List<IntRange>): AnnotatedString {
    val background = MaterialTheme.colorScheme.primaryContainer
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    return remember(text, ranges, background, foreground) { highlightedText(text, ranges, background, foreground) }
}
