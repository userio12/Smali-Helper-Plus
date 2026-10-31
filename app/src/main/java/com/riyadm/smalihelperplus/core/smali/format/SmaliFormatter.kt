package com.riyadm.smalihelperplus.core.smali.format

import com.riyadm.smalihelperplus.core.smali.diagnostics.Diagnostic
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity
import com.riyadm.smalihelperplus.core.smali.lexer.LexedLine
import com.riyadm.smalihelperplus.core.smali.lexer.SmaliLexer
import com.riyadm.smalihelperplus.core.smali.lexer.Token
import com.riyadm.smalihelperplus.core.smali.lexer.TokenType

/**
 * Result of formatting. When [refused] is true, [text] is the unmodified input and [diagnostics] explains why.
 */
data class FormatResult(val text: String, val changed: Boolean, val refused: Boolean, val diagnostics: List<Diagnostic>)

/**
 * Canonical smali formatter.
 *
 * - 4 spaces per block level (method bodies, annotation bodies, payload bodies, field/param annotation blocks).
 * - Whitespace between tokens is normalised: a single space after commas and none before, no padding inside `{}`,
 *   single spaces around `..`; tokens that were written without whitespace between them stay adjacent.
 * - Comments, string literals and every token are preserved verbatim; trailing whitespace outside comments is trimmed; runs of blank
 *   lines collapse to one and members (`.field`/`.method`) are separated by a blank line.
 *
 * The formatter only changes whitespace, so it can never change the meaning of the code. If the block structure cannot
 * be understood (unterminated literals, unbalanced `.end` directives) the input is returned untouched.
 */
object SmaliFormatter {
    const val INDENT = "    "

    private enum class Block { METHOD, ANNOTATION, SUBANNOTATION, PAYLOAD, FIELD, PARAM, BRACE }

    private class Refusal(val line: Int, val reason: String) : Exception(reason)

    fun format(text: String): FormatResult {
        val lines = SmaliLexer.lex(text)
        lines.forEach { l ->
            l.tokens.firstOrNull { it.type == TokenType.ERROR }?.let {
                return refuse(text, l.number, "Formatting skipped: unterminated literal on line ${l.number}")
            }
        }
        val formatted = try {
            layout(lines)
        } catch (r: Refusal) {
            return refuse(text, r.line, "Formatting skipped: ${r.reason}")
        }
        return FormatResult(formatted, formatted != text, false, emptyList())
    }

    private fun refuse(text: String, line: Int, message: String) = FormatResult(
        text, false, true,
        listOf(Diagnostic(Severity.WARNING, DiagnosticCode.SYNTAX, message, line, suggestion = "Fix the reported problem and format again")),
    )

    private fun layout(lines: List<LexedLine>): String {
        val stack = ArrayList<Block>()
        val out = ArrayList<String>()
        var pendingBlank = false

        fun emit(indent: Int, content: String, isMemberStart: Boolean) {
            val last = out.lastOrNull()
            val lastIsComment = last != null && last.trimStart().startsWith("#")
            if (out.isNotEmpty() && (pendingBlank || (isMemberStart && last != "" && !lastIsComment))) {
                if (out.last() != "") out += ""
            }
            pendingBlank = false
            out += INDENT.repeat(indent) + content
        }

        for (line in lines) {
            if (line.isBlank) {
                if (out.isNotEmpty()) pendingBlank = true
                continue
            }
            val code = line.codeTokens
            val first = code.firstOrNull()
            val second = code.getOrNull(1)
            val directive = if (first?.type == TokenType.DIRECTIVE) first.text else null
            val isEnd = directive == ".end"
            val endWhat = if (isEnd) second?.text else null

            // Optional blocks (.field / .param) end implicitly at anything other than an annotation or their .end.
            val top = stack.lastOrNull()
            if ((top == Block.FIELD || top == Block.PARAM) && first != null && directive != ".annotation" &&
                !(isEnd && (endWhat == "field" || endWhat == "param"))
            ) {
                stack.removeAt(stack.lastIndex)
            }

            // Closing lines are indented at the level of their opening line.
            when {
                isEnd -> {
                    val expected = when (endWhat) {
                        "method" -> Block.METHOD
                        "annotation" -> Block.ANNOTATION
                        "subannotation" -> Block.SUBANNOTATION
                        "packed-switch", "sparse-switch", "array-data" -> Block.PAYLOAD
                        "field" -> Block.FIELD
                        "param" -> Block.PARAM
                        "local" -> null
                        else -> throw Refusal(line.number, "unknown `.end ${endWhat ?: ""}` on line ${line.number}")
                    }
                    if (expected != null) {
                        if (stack.lastOrNull() != expected) {
                            throw Refusal(line.number, "`.end $endWhat` on line ${line.number} does not close an open block")
                        }
                        stack.removeAt(stack.lastIndex)
                    }
                }
                first?.type == TokenType.RBRACE && stack.lastOrNull() == Block.BRACE -> stack.removeAt(stack.lastIndex)
            }

            val content = renderLine(line)
            val isMember = stack.isEmpty() && (directive == ".method" || directive == ".field")
            // Comment-only lines next to an optional (.field/.param) block belong to the enclosing level.
            val inOptional = stack.lastOrNull().let { it == Block.FIELD || it == Block.PARAM }
            emit(if (first == null && inOptional) stack.size - 1 else stack.size, content, isMember)

            // Annotation values may open a subannotation after `name =`.
            if (directive != ".subannotation" && code.any { it.type == TokenType.DIRECTIVE && it.text == ".subannotation" }) {
                stack += Block.SUBANNOTATION
            }
            // Openers.
            when (directive) {
                ".method" -> stack += Block.METHOD
                ".annotation" -> stack += Block.ANNOTATION
                ".subannotation" -> stack += Block.SUBANNOTATION
                ".packed-switch", ".sparse-switch", ".array-data" -> stack += Block.PAYLOAD
                ".field" -> stack += Block.FIELD
                ".param" -> stack += Block.PARAM
            }
            if (stack.lastOrNull().let { it == Block.ANNOTATION || it == Block.SUBANNOTATION || it == Block.BRACE }) {
                if (code.lastOrNull()?.type == TokenType.LBRACE) stack += Block.BRACE
            }
            if (isEnd && (endWhat == "method" || endWhat == "field") && stack.isEmpty()) pendingBlank = true
        }
        while (stack.lastOrNull() == Block.FIELD || stack.lastOrNull() == Block.PARAM) stack.removeAt(stack.lastIndex)
        if (stack.isNotEmpty()) {
            throw Refusal(lines.lastOrNull()?.number ?: 1, "a `${stack.last().name.lowercase()}` block is not closed")
        }
        while (out.lastOrNull() == "") out.removeAt(out.lastIndex)
        return if (out.isEmpty()) "" else out.joinToString("\n", postfix = "\n")
    }

    /** Re-emits the tokens of a line with canonical spacing; comments and literals are copied verbatim. */
    internal fun renderLine(line: LexedLine): String {
        val sb = StringBuilder()
        var prev: Token? = null
        for (t in line.tokens) {
            if (prev != null) {
                if (t.type == TokenType.COMMENT) {
                    sb.append(' ')
                } else if (needsSpace(prev, t)) {
                    sb.append(' ')
                }
            }
            sb.append(t.text)
            prev = t
        }
        return sb.toString()
    }

    private fun needsSpace(a: Token, b: Token): Boolean = when {
        b.type == TokenType.COMMA -> false
        a.type == TokenType.COMMA -> true
        a.type == TokenType.DOTDOT || b.type == TokenType.DOTDOT -> true
        a.type == TokenType.LBRACE -> false
        b.type == TokenType.RBRACE -> false
        else -> a.endColumn != b.column // keep tokens that were written adjacent together
    }
}
