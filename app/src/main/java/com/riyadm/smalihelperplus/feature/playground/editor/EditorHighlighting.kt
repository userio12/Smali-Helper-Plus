package com.riyadm.smalihelperplus.feature.playground.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliHighlighter
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliToken
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliTokenKind
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeColors

/** A background decoration over [range] (search matches, register occurrences, bracket pairs, diagnostics). */
@Immutable
data class EditorDecoration(val range: IntRange, val background: Color, val underline: Boolean = false)

/**
 * Incremental syntax highlighter for the editor: token lists are cached per line text, so typing only re-tokenizes
 * the edited line. The last full result is cached by text identity, so cursor moves reuse it.
 */
class EditorHighlighter(private val maxCachedLines: Int = 8192) {
    private val lineCache = object : LinkedHashMap<String, List<SmaliToken>>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SmaliToken>>?): Boolean = size > maxCachedLines
    }
    private var lastText: String? = null
    private var lastColors: CodeColors? = null
    private var lastResult: AnnotatedString? = null

    fun highlight(text: String, colors: CodeColors): AnnotatedString {
        if (text === lastText && colors == lastColors) return lastResult!!
        if (text == lastText && colors == lastColors) return lastResult!!
        val styles = styles(colors)
        val builder = AnnotatedString.Builder(text)
        if (text.length <= MAX_HIGHLIGHT_CHARS) {
            var start = 0
            while (start <= text.length) {
                var end = text.indexOf('\n', start)
                if (end < 0) end = text.length
                if (end > start) {
                    val line = text.substring(start, end)
                    val tokens = lineCache.getOrPut(line) { SmaliHighlighter.tokenize(line) }
                    for (t in tokens) styles[t.kind]?.let { builder.addStyle(it, start + t.start, start + t.end) }
                }
                start = end + 1
            }
        }
        val result = builder.toAnnotatedString()
        lastText = text
        lastColors = colors
        lastResult = result
        return result
    }

    private var cachedStylesFor: CodeColors? = null
    private var cachedStyles: Map<SmaliTokenKind, SpanStyle> = emptyMap()

    private fun styles(c: CodeColors): Map<SmaliTokenKind, SpanStyle> {
        if (c == cachedStylesFor) return cachedStyles
        cachedStyles = mapOf(
            SmaliTokenKind.COMMENT to SpanStyle(color = c.comment, fontStyle = FontStyle.Italic),
            SmaliTokenKind.DIRECTIVE to SpanStyle(color = c.directive, fontWeight = FontWeight.SemiBold),
            SmaliTokenKind.OPCODE to SpanStyle(color = c.opcode, fontWeight = FontWeight.SemiBold),
            SmaliTokenKind.REGISTER to SpanStyle(color = c.register),
            SmaliTokenKind.PARAM_REGISTER to SpanStyle(color = c.parameterRegister),
            SmaliTokenKind.STRING to SpanStyle(color = c.string),
            SmaliTokenKind.NUMBER to SpanStyle(color = c.number),
            SmaliTokenKind.TYPE to SpanStyle(color = c.type),
            SmaliTokenKind.LABEL to SpanStyle(color = c.label),
            SmaliTokenKind.KEYWORD to SpanStyle(color = c.keyword),
            SmaliTokenKind.MEMBER to SpanStyle(color = c.member),
        )
        cachedStylesFor = c
        return cachedStyles
    }

    companion object {
        /** Above this size syntax colours are skipped to keep typing responsive (decorations still apply). */
        const val MAX_HIGHLIGHT_CHARS = 400_000
    }
}

/** Identity-mapped visual transformation that adds syntax colours and decorations. */
class SmaliVisualTransformation(
    private val highlighter: EditorHighlighter,
    private val colors: CodeColors,
    private val decorations: List<EditorDecoration>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val base = highlighter.highlight(text.text, colors)
        val out = if (decorations.isEmpty()) {
            base
        } else {
            AnnotatedString.Builder(base).apply {
                val len = text.length
                decorations.forEach { d ->
                    val s = d.range.first.coerceIn(0, len)
                    val e = (d.range.last + 1).coerceIn(0, len)
                    if (e > s) {
                        addStyle(
                            SpanStyle(background = d.background, textDecoration = if (d.underline) TextDecoration.Underline else null),
                            s, e,
                        )
                    }
                }
            }.toAnnotatedString()
        }
        return TransformedText(out, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean =
        other is SmaliVisualTransformation && other.colors == colors && other.decorations == decorations && other.highlighter === highlighter

    override fun hashCode(): Int = 31 * colors.hashCode() + decorations.hashCode()
}
