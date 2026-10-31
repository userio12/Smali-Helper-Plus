package com.riyadm.smalihelperplus.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeColors

/** Token kinds produced by [SmaliHighlighter.tokenize]. */
enum class SmaliTokenKind { COMMENT, DIRECTIVE, OPCODE, REGISTER, PARAM_REGISTER, STRING, NUMBER, TYPE, LABEL, KEYWORD, MEMBER, PLAIN }

data class SmaliToken(val start: Int, val end: Int, val kind: SmaliTokenKind)

/**
 * Lightweight, allocation-friendly Smali tokenizer for syntax highlighting of read-only code blocks and the editor.
 * It is intentionally forgiving: it never throws and highlights partially typed code.
 */
object SmaliHighlighter {
    private val KEYWORDS = setOf(
        "public", "private", "protected", "static", "final", "synchronized", "volatile", "transient", "native",
        "interface", "abstract", "strictfp", "synthetic", "annotation", "enum", "constructor", "bridge", "varargs",
        "declared-synchronized", "system", "build", "runtime",
    )

    fun tokenize(code: String): List<SmaliToken> {
        val tokens = ArrayList<SmaliToken>(code.length / 4)
        var lineStart = 0
        while (lineStart <= code.length) {
            var lineEnd = code.indexOf('\n', lineStart)
            if (lineEnd == -1) lineEnd = code.length
            tokenizeLine(code, lineStart, lineEnd, tokens)
            lineStart = lineEnd + 1
        }
        return tokens
    }

    private fun tokenizeLine(s: String, start: Int, end: Int, out: MutableList<SmaliToken>) {
        var i = start
        var firstWord = true
        while (i < end) {
            val c = s[i]
            when {
                c == ' ' || c == '\t' || c == ',' || c == '{' || c == '}' || c == '(' || c == ')' -> i++
                c == '#' -> { out += SmaliToken(i, end, SmaliTokenKind.COMMENT); return }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < end && s[j] != c) { if (s[j] == '\\') j++; j++ }
                    val stop = minOf(j + 1, end)
                    out += SmaliToken(i, stop, SmaliTokenKind.STRING); i = stop; firstWord = false
                }
                c == '.' && firstWord -> {
                    val j = wordEnd(s, i + 1, end)
                    out += SmaliToken(i, j, SmaliTokenKind.DIRECTIVE); i = j; firstWord = false
                }
                c == ':' -> {
                    val j = wordEnd(s, i + 1, end)
                    out += SmaliToken(i, j, SmaliTokenKind.LABEL); i = j; firstWord = false
                }
                c == '-' && i + 1 < end && s[i + 1] == '>' -> {
                    var j = i + 2
                    while (j < end && (s[j].isLetterOrDigit() || s[j] == '_' || s[j] == '$' || s[j] == '<' || s[j] == '>' || s[j] == '-')) j++
                    out += SmaliToken(i, j, SmaliTokenKind.MEMBER); i = j
                }
                c == 'L' && isTypeStart(s, i, end) -> {
                    val j = s.indexOf(';', i).let { if (it == -1 || it >= end) end else it + 1 }
                    out += SmaliToken(i, j, SmaliTokenKind.TYPE); i = j; firstWord = false
                }
                c == '[' -> {
                    var j = i
                    while (j < end && s[j] == '[') j++
                    if (j < end && s[j] == 'L') j = s.indexOf(';', j).let { if (it == -1 || it >= end) end else it + 1 }
                    else if (j < end) j++
                    out += SmaliToken(i, j, SmaliTokenKind.TYPE); i = j; firstWord = false
                }
                c.isDigit() || (c == '-' && i + 1 < end && s[i + 1].isDigit()) -> {
                    var j = i + 1
                    while (j < end && (s[j].isLetterOrDigit() || s[j] == '.')) j++
                    out += SmaliToken(i, j, SmaliTokenKind.NUMBER); i = j; firstWord = false
                }
                c.isLetter() || c == '_' || c == '<' || c == '$' -> {
                    val j = wordEnd(s, i, end)
                    val word = s.substring(i, j)
                    val kind = when {
                        isRegister(word, 'v') -> SmaliTokenKind.REGISTER
                        isRegister(word, 'p') -> SmaliTokenKind.PARAM_REGISTER
                        firstWord -> SmaliTokenKind.OPCODE
                        word in KEYWORDS -> SmaliTokenKind.KEYWORD
                        word.length == 1 && word[0] in "VZBSCIJFD" && j < end && (s[j] == ')' || s[j] == ';') -> SmaliTokenKind.TYPE
                        else -> SmaliTokenKind.PLAIN
                    }
                    if (kind != SmaliTokenKind.PLAIN) out += SmaliToken(i, j, kind)
                    i = j; firstWord = false
                }
                else -> i++
            }
        }
    }

    private fun isTypeStart(s: String, i: Int, end: Int): Boolean {
        if (i > 0 && (s[i - 1].isLetterOrDigit() || s[i - 1] == '_')) return false
        val semi = s.indexOf(';', i)
        if (semi == -1 || semi >= end) return false
        for (k in i + 1 until semi) { val ch = s[k]; if (ch == ' ' || ch == ',' || ch == '"') return false }
        return semi > i + 1
    }

    private fun isRegister(word: String, prefix: Char) =
        word.length >= 2 && word[0] == prefix && word.substring(1).all { it.isDigit() }

    private fun wordEnd(s: String, from: Int, end: Int): Int {
        var j = from
        while (j < end && (s[j].isLetterOrDigit() || s[j] in "_-/$<>.")) j++
        return j
    }

    fun highlight(code: String, colors: CodeColors): AnnotatedString = buildAnnotatedString {
        append(code)
        tokenize(code).forEach { t ->
            val style = when (t.kind) {
                SmaliTokenKind.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                SmaliTokenKind.DIRECTIVE -> SpanStyle(color = colors.directive, fontWeight = FontWeight.SemiBold)
                SmaliTokenKind.OPCODE -> SpanStyle(color = colors.opcode, fontWeight = FontWeight.SemiBold)
                SmaliTokenKind.REGISTER -> SpanStyle(color = colors.register)
                SmaliTokenKind.PARAM_REGISTER -> SpanStyle(color = colors.parameterRegister)
                SmaliTokenKind.STRING -> SpanStyle(color = colors.string)
                SmaliTokenKind.NUMBER -> SpanStyle(color = colors.number)
                SmaliTokenKind.TYPE -> SpanStyle(color = colors.type)
                SmaliTokenKind.LABEL -> SpanStyle(color = colors.label)
                SmaliTokenKind.KEYWORD -> SpanStyle(color = colors.keyword)
                SmaliTokenKind.MEMBER -> SpanStyle(color = colors.member)
                SmaliTokenKind.PLAIN -> null
            }
            if (style != null) addStyle(style, t.start, t.end)
        }
    }
}
