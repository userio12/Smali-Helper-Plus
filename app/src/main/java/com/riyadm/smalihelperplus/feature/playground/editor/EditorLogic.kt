package com.riyadm.smalihelperplus.feature.playground.editor

import com.riyadm.smalihelperplus.core.smali.opcodes.DalvikOpcodeTable
import com.riyadm.smalihelperplus.core.smali.parser.SmaliParser
import com.riyadm.smalihelperplus.core.smali.lexer.SmaliLexer
import com.riyadm.smalihelperplus.core.smali.lexer.TokenType

/** Plain text + selection, independent of Compose types so the logic is unit-testable. */
data class EditorText(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart) {
    val cursor: Int get() = selectionEnd
    val isCollapsed: Boolean get() = selectionStart == selectionEnd
}

/** Pure editing helpers used by the Playground editor. */
object EditorLogic {
    const val INDENT = "    "
    private val BLOCK_OPENERS = setOf(".method", ".annotation", ".subannotation", ".packed-switch", ".sparse-switch", ".array-data")
    private val REGISTER = Regex("[vp][0-9]+")

    // ---------------------------------------------------------------------------------------------------------------
    // Lines
    // ---------------------------------------------------------------------------------------------------------------

    fun lineStarts(text: String): IntArray {
        var count = 1
        for (c in text) if (c == '\n') count++
        val out = IntArray(count)
        var idx = 1
        for (i in text.indices) if (text[i] == '\n') out[idx++] = i + 1
        return out
    }

    /** 0-based line index of [offset] given precomputed [starts]. */
    fun lineOf(starts: IntArray, offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun lineStartBefore(text: String, offset: Int): Int = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(-1)).let { if (offset == 0) 0 else it + 1 }

    private fun leadingWhitespace(line: String): String = line.takeWhile { it == ' ' || it == '\t' }

    // ---------------------------------------------------------------------------------------------------------------
    // Auto-indent
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Applies auto-indentation when [new] differs from [old] by a single newline typed at the cursor:
     * keeps the previous line's indentation, indents after block openers (`.method`, `.annotation`, payloads, `{`) and
     * dedents a just-finished `.end ...` line to the level of its block opener. Returns null when no change applies.
     */
    fun autoIndent(old: EditorText, new: EditorText): EditorText? {
        if (new.text.length != old.text.length - kotlin.math.abs(old.selectionEnd - old.selectionStart) + 1) return null
        if (!new.isCollapsed || new.cursor == 0 || new.text[new.cursor - 1] != '\n') return null
        val insertAt = minOf(old.selectionStart, old.selectionEnd)
        if (new.cursor != insertAt + 1) return null
        // The text before and after the inserted newline must be unchanged.
        val oldEnd = maxOf(old.selectionStart, old.selectionEnd)
        if (!new.text.regionMatches(0, old.text, 0, insertAt)) return null
        if (!new.text.regionMatches(new.cursor, old.text, oldEnd, old.text.length - oldEnd)) return null

        var text = new.text
        var cursor = new.cursor
        val prevLineStart = lineStartBefore(text, cursor - 1)
        var prevLine = text.substring(prevLineStart, cursor - 1)
        var indent = leadingWhitespace(prevLine)

        val codeTokens = SmaliLexer.lexLine(prevLine, 1).filter { it.type != TokenType.COMMENT }
        val closesBlock = codeTokens.firstOrNull()?.text == ".end" &&
            ("." + codeTokens.getOrNull(1)?.text) in BLOCK_OPENERS
        if (closesBlock) {
            // Dedent the finished `.end` line to its opener's level.
            val target = openerIndentFor(text, prevLineStart)
            if (target != null && target != indent) {
                val fixed = target + prevLine.trimStart()
                text = text.substring(0, prevLineStart) + fixed + text.substring(prevLineStart + prevLine.length)
                cursor += fixed.length - prevLine.length
                prevLine = fixed
                indent = target
            }
        } else {
            val first = codeTokens.firstOrNull()?.text
            if (first in BLOCK_OPENERS || codeTokens.lastOrNull()?.type == TokenType.LBRACE) indent += INDENT
        }
        // Remove whitespace that followed the cursor on the split line so the new line starts at the indent.
        var after = cursor
        while (after < text.length && (text[after] == ' ' || text[after] == '\t')) after++
        text = text.substring(0, cursor) + indent + text.substring(after)
        cursor += indent.length
        return EditorText(text, cursor)
    }

    /** Finds the indentation of the unmatched block opener above [lineStart], or null. */
    private fun openerIndentFor(text: String, lineStart: Int): String? {
        var depth = 0
        var end = lineStart - 1
        while (end > 0) {
            val start = lineStartBefore(text, end)
            val line = text.substring(start, end)
            val t = line.trim()
            val first = t.takeWhile { !it.isWhitespace() }
            when {
                t.startsWith(".end ") && ("." + t.removePrefix(".end ").trim().substringBefore(' ')) in BLOCK_OPENERS -> depth++
                first in BLOCK_OPENERS -> {
                    if (depth == 0) return leadingWhitespace(line)
                    depth--
                }
            }
            end = start - 1
        }
        return if (end <= 0 && depth == 0) "" else null
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------------------------------

    fun findMatches(text: String, query: String, matchCase: Boolean, limit: Int = 5000): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (out.size < limit) {
            val idx = text.indexOf(query, from, ignoreCase = !matchCase)
            if (idx < 0) break
            out += idx until idx + query.length
            from = idx + maxOf(1, query.length)
        }
        return out
    }

    /** Replaces every occurrence and returns the new text and the number of replacements. */
    fun replaceAll(text: String, query: String, replacement: String, matchCase: Boolean): Pair<String, Int> {
        val matches = findMatches(text, query, matchCase, Int.MAX_VALUE)
        if (matches.isEmpty()) return text to 0
        val sb = StringBuilder()
        var last = 0
        matches.forEach { r ->
            sb.append(text, last, r.first).append(replacement)
            last = r.last + 1
        }
        sb.append(text, last, text.length)
        return sb.toString() to matches.size
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tokens around the cursor
    // ---------------------------------------------------------------------------------------------------------------

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '/' || c == '_' || c == '.' || c == '$'

    /** Range of the word (mnemonic/register/directive-like) touching [offset], or null. */
    fun wordAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        var start = offset.coerceIn(0, text.length)
        var end = start
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.length && isWordChar(text[end])) end++
        return if (end > start) start until end else null
    }

    /** The register token (vN/pN) touching [offset], or null. */
    fun registerAt(text: String, offset: Int): String? {
        val r = wordAt(text, offset) ?: return null
        val w = text.substring(r.first, r.last + 1)
        return w.takeIf { REGISTER.matches(it) }
    }

    /** All occurrences of [register] as a whole word, ignoring comments and string literals. */
    fun registerOccurrences(text: String, register: String, limit: Int = 2000): List<IntRange> {
        if (!REGISTER.matches(register) || limit <= 0) return emptyList()
        val out = ArrayList<IntRange>()
        var lineStart = 0
        while (lineStart <= text.length && out.size < limit) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            var i = lineStart
            var inString = false
            while (i < lineEnd && out.size < limit) {
                val c = text[i]
                if (inString) {
                    if (c == '\\') i++ else if (c == '"') inString = false
                    i++
                    continue
                }
                if (c == '"') { inString = true; i++; continue }
                if (c == '#') break
                if (text.startsWith(register, i) &&
                    (i == 0 || !isWordChar(text[i - 1])) &&
                    (i + register.length >= text.length || !isWordChar(text[i + register.length]))
                ) {
                    out += i until i + register.length
                    i += register.length
                    continue
                }
                i++
            }
            lineStart = lineEnd + 1
        }
        return out
    }

    /** Offsets of a bracket next to [cursor] and its matching partner (`{}` and `()`), skipping strings/comments. */
    fun matchingBracket(text: String, cursor: Int, maxScan: Int = 20000): Pair<Int, Int>? {
        if (maxScan <= 0) return null
        val ignored = SmaliLexer.tokenize(text)
            .filter { it.type in setOf(
                TokenType.STRING,
                TokenType.CHAR,
                TokenType.COMMENT,
                TokenType.ERROR,
            ) }
        val masked = BooleanArray(text.length)
        ignored.forEach { t -> for (i in t.offset until t.endOffset) masked[i] = true }
        val candidates = listOf(cursor - 1, cursor).filter { it in text.indices }
        for (pos in candidates) {
            val c = text[pos]
            val (open, close, forward) = when (c) {
                '{' -> Triple('{', '}', true)
                '(' -> Triple('(', ')', true)
                '}' -> Triple('{', '}', false)
                ')' -> Triple('(', ')', false)
                else -> continue
            }
            if (masked[pos]) continue
            var depth = 0
            var i = pos
            var steps = 0
            while (i in text.indices && steps++ < maxScan) {
                val ch = text[i]
                if (ch == open || ch == close) {
                    if (!masked[i]) {
                        depth += if ((ch == open) == forward) 1 else -1
                        if (depth == 0) return pos to i
                    }
                }
                i += if (forward) 1 else -1
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Autocomplete
    // ---------------------------------------------------------------------------------------------------------------

    data class Completion(val start: Int, val end: Int, val prefix: String, val suggestions: List<String>)

    private val directiveWords: List<String> by lazy {
        SmaliParser.DIRECTIVES.filter { !it.startsWith(".end ") } + listOf(".end method", ".end field", ".end annotation", ".end param")
    }

    /**
     * Suggestions for the word being typed at the start of a line: opcode mnemonics (from the opcode table) and
     * directives. Returns null when no completion applies.
     */
    fun completionAt(text: String, cursor: Int, max: Int = 12): Completion? {
        if (cursor < 0 || cursor > text.length) return null
        var start = cursor
        while (start > 0 && isWordChar(text[start - 1])) start--
        if (start == cursor) return null
        val lineStart = lineStartBefore(text, start)
        if (text.substring(lineStart, start).isNotBlank()) return null // only the first word of a line
        var end = cursor
        while (end < text.length && isWordChar(text[end])) end++
        val prefix = text.substring(start, cursor)
        val pool = if (prefix.startsWith(".")) directiveWords else DalvikOpcodeTable.mnemonics
        val exact = text.substring(start, end)
        val list = pool.filter { it.startsWith(prefix, ignoreCase = true) && it != exact }
            .sortedWith(compareBy({ it.length }, { it }))
            .take(max)
        return if (list.isEmpty()) null else Completion(start, end, prefix, list)
    }

    /** Applies [suggestion] to [completion], adding a trailing space. */
    fun applyCompletion(text: String, completion: Completion, suggestion: String): EditorText {
        val needsSpace = completion.end >= text.length || text[completion.end] != ' '
        val insert = suggestion + if (needsSpace) " " else ""
        val newText = text.substring(0, completion.start) + insert + text.substring(completion.end)
        val cursor = completion.start + insert.length + if (needsSpace) 0 else 1
        return EditorText(newText, cursor.coerceAtMost(newText.length))
    }
}

/**
 * Bounded undo/redo history of editor states. Consecutive single-character typing is coalesced into one step.
 */
class EditHistory(private val maxEntries: Int = 200) {
    private val undo = ArrayDeque<EditorText>()
    private val redo = ArrayDeque<EditorText>()
    private var lastRecordMillis = 0L
    private var lastAfter: EditorText? = null
    private var lastDelta = 0

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /**
     * Records that the editor changed from [before]. Typing bursts (single-character edits within [coalesceMillis])
     * are merged; [force] always creates a new step (format, paste, replace all, file loads...).
     */
    fun record(before: EditorText, after: EditorText, nowMillis: Long, force: Boolean = false, coalesceMillis: Long = 1000) {
        if (before.text == after.text) return
        val delta = after.text.length - before.text.length
        val small = delta == 1 || delta == -1
        val typedNewline = delta == 1 && after.cursor > 0 && after.text.getOrNull(after.cursor - 1) == '\n'
        val coalesce = !force && small && !typedNewline && before.isCollapsed && after.isCollapsed &&
            before == lastAfter && delta == lastDelta && undo.isNotEmpty() &&
            nowMillis - lastRecordMillis in 0 until coalesceMillis
        if (!coalesce) {
            undo.addLast(before)
            while (undo.size > maxEntries.coerceAtLeast(0)) undo.removeFirst()
        }
        lastRecordMillis = nowMillis
        lastAfter = if (force || !small || typedNewline) null else after
        lastDelta = delta
        redo.clear()
    }

    fun undo(current: EditorText): EditorText? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastRecordMillis = 0
        lastAfter = null
        return prev
    }

    fun redo(current: EditorText): EditorText? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastRecordMillis = 0
        lastAfter = null
        return next
    }

    fun clear() {
        undo.clear()
        redo.clear()
        lastAfter = null
    }
}
