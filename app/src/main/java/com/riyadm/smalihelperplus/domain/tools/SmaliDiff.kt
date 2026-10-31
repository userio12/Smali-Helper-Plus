package com.riyadm.smalihelperplus.domain.tools

data class DiffOptions(
    val ignoreWhitespace: Boolean = false,
    val ignoreComments: Boolean = false,
    val ignoreLineDirectives: Boolean = false,
)

enum class DiffKind { EQUAL, ADDED, REMOVED, CHANGED }

/** Half-open character range [start, end) inside a line. */
data class TextSpan(val start: Int, val end: Int)

/** A line in the unified view. [kind] is EQUAL, ADDED or REMOVED. */
data class UnifiedLine(
    val kind: DiffKind,
    val text: String,
    val leftLine: Int?,
    val rightLine: Int?,
    /** Intra-line changed ranges (only for lines that belong to a changed pair). */
    val highlights: List<TextSpan> = emptyList(),
)

data class SideCell(val lineNumber: Int, val text: String, val highlights: List<TextSpan> = emptyList())

/** A row in the side-by-side view. EQUAL rows have both cells; CHANGED rows have both cells with highlights. */
data class SideRow(val kind: DiffKind, val left: SideCell?, val right: SideCell?)

data class DiffStats(val added: Int, val removed: Int, val changed: Int, val unchanged: Int) {
    val total: Int get() = added + removed + changed
}

data class DiffResult(
    val unified: List<UnifiedLine>,
    val sideBySide: List<SideRow>,
    val stats: DiffStats,
    /** True when the inputs were so different that the exact minimal diff was skipped for part of the text. */
    val approximate: Boolean,
) {
    val identical: Boolean get() = stats.total == 0

    /** Plain unified-diff text with [context] lines around changes (for copy / share). */
    fun toPatchText(leftName: String = "left", rightName: String = "right", context: Int = 3): String {
        if (identical) return "No differences"
        val sb = StringBuilder()
        sb.append("--- ").append(leftName).append('\n').append("+++ ").append(rightName).append('\n')
        val changeIdx = unified.indices.filter { unified[it].kind != DiffKind.EQUAL }
        var i = 0
        while (i < changeIdx.size) {
            var start = (changeIdx[i] - context).coerceAtLeast(0)
            var end = (changeIdx[i] + context).coerceAtMost(unified.lastIndex)
            var j = i + 1
            while (j < changeIdx.size && changeIdx[j] - context <= end + 1) {
                end = (changeIdx[j] + context).coerceAtMost(unified.lastIndex)
                j++
            }
            val slice = unified.subList(start, end + 1)
            val leftStart = slice.firstNotNullOfOrNull { it.leftLine } ?: 0
            val rightStart = slice.firstNotNullOfOrNull { it.rightLine } ?: 0
            val leftCount = slice.count { it.leftLine != null }
            val rightCount = slice.count { it.rightLine != null }
            sb.append("@@ -$leftStart,$leftCount +$rightStart,$rightCount @@\n")
            for (line in slice) {
                sb.append(
                    when (line.kind) {
                        DiffKind.ADDED -> '+'
                        DiffKind.REMOVED -> '-'
                        else -> ' '
                    },
                ).append(line.text).append('\n')
            }
            i = j
            start = end
        }
        return sb.toString().trimEnd('\n')
    }
}

/**
 * Line based diff (Myers O(ND)) tuned for smali: optional ignoring of whitespace, comments and `.line` directives,
 * unified and side-by-side models, and token level highlights inside changed line pairs.
 */
object SmaliDiff {

    private const val MAX_EDIT_DISTANCE = 2500

    private class Entry(val number: Int, val text: String, val key: String)

    fun diff(left: String, right: String, options: DiffOptions = DiffOptions()): DiffResult {
        val a = entries(left, options)
        val b = entries(right, options)

        // Map each distinct key to an int id.
        val ids = HashMap<String, Int>()
        val ak = IntArray(a.size) { ids.getOrPut(a[it].key) { ids.size } }
        val bk = IntArray(b.size) { ids.getOrPut(b[it].key) { ids.size } }

        // Trim common prefix / suffix.
        var prefix = 0
        while (prefix < ak.size && prefix < bk.size && ak[prefix] == bk[prefix]) prefix++
        var suffix = 0
        while (suffix < ak.size - prefix && suffix < bk.size - prefix && ak[ak.size - 1 - suffix] == bk[bk.size - 1 - suffix]) suffix++

        val midA = ak.copyOfRange(prefix, ak.size - suffix)
        val midB = bk.copyOfRange(prefix, bk.size - suffix)
        val pairs = ArrayList<Pair<Int, Int>>()
        for (i in 0 until prefix) pairs += i to i
        var approximate = false
        if (midA.isNotEmpty() && midB.isNotEmpty()) {
            val mid = myers(midA, midB)
            if (mid == null) approximate = true else mid.forEach { (i, j) -> pairs += (i + prefix) to (j + prefix) }
        }
        for (s in 0 until suffix) pairs += (ak.size - suffix + s) to (bk.size - suffix + s)

        return build(a, b, pairs, options, approximate)
    }

    private fun entries(text: String, options: DiffOptions): List<Entry> {
        if (text.isEmpty()) return emptyList()
        val lines = text.split('\n').map { it.removeSuffix("\r") }.let { if (it.last().isEmpty() && it.size > 1) it.dropLast(1) else it }
        val out = ArrayList<Entry>(lines.size)
        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (options.ignoreLineDirectives && (trimmed == ".line" || trimmed.startsWith(".line "))) continue
            if (options.ignoreComments && trimmed.startsWith("#")) continue
            var key = line
            if (options.ignoreComments) key = stripComment(key)
            if (options.ignoreWhitespace) {
                key = key.trim().replace(Regex("\\s+"), " ")
                if (key.isEmpty()) continue
            }
            out += Entry(idx + 1, line, key)
        }
        return out
    }

    /** Removes a trailing `# comment`, honoring quotes. */
    fun stripComment(line: String): String {
        var inString = false
        var quote = ' '
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inString) {
                if (c == '\\') i++ else if (c == quote) inString = false
            } else if (c == '"' || c == '\'') {
                inString = true; quote = c
            } else if (c == '#') {
                return line.substring(0, i).trimEnd()
            }
            i++
        }
        return line
    }

    /** Returns matched index pairs (into a, into b) of a longest common subsequence, or null when too different. */
    private fun myers(a: IntArray, b: IntArray): List<Pair<Int, Int>>? {
        val n = a.size
        val m = b.size
        val maxD = minOf(n + m, MAX_EDIT_DISTANCE)
        val off = maxD + 1
        val v = IntArray(2 * maxD + 3)
        val trace = ArrayList<IntArray>()
        var finalD = -1
        outer@ for (d in 0..maxD) {
            trace += v.copyOfRange(off - d, off + d + 1)
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[off + k - 1] < v[off + k + 1])) v[off + k + 1] else v[off + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) { x++; y++ }
                v[off + k] = x
                if (x >= n && y >= m) { finalD = d; break@outer }
                k += 2
            }
        }
        if (finalD < 0) return null
        val matches = ArrayList<Pair<Int, Int>>()
        var x = n
        var y = m
        for (d in finalD downTo 1) {
            val snap = trace[d] // v state before step d; index = k + d
            val k = x - y
            val prevK = if (k == -d || (k != d && snap[k - 1 + d] < snap[k + 1 + d])) k + 1 else k - 1
            val prevX = snap[prevK + d]
            val prevY = prevX - prevK
            while (x > prevX && y > prevY && x > 0 && y > 0 && a[x - 1] == b[y - 1]) {
                matches += (x - 1) to (y - 1)
                x--; y--
            }
            x = prevX
            y = prevY
        }
        // Remaining diagonal at d = 0.
        while (x > 0 && y > 0) {
            matches += (x - 1) to (y - 1)
            x--; y--
        }
        matches.reverse()
        return matches
    }

    private fun build(a: List<Entry>, b: List<Entry>, pairs: List<Pair<Int, Int>>, options: DiffOptions, approximate: Boolean): DiffResult {
        val unified = ArrayList<UnifiedLine>()
        val side = ArrayList<SideRow>()
        var added = 0
        var removed = 0
        var changed = 0
        var unchanged = 0
        var ia = 0
        var ib = 0

        fun flush(toA: Int, toB: Int) {
            val del = a.subList(ia, toA)
            val ins = b.subList(ib, toB)
            if (del.isEmpty() && ins.isEmpty()) return
            val paired = minOf(del.size, ins.size)
            val delHighlights = ArrayList<List<TextSpan>>()
            val insHighlights = ArrayList<List<TextSpan>>()
            for (p in 0 until paired) {
                val (l, r) = intraLine(del[p].text, ins[p].text, options.ignoreWhitespace)
                delHighlights += l
                insHighlights += r
            }
            for ((idx, e) in del.withIndex()) {
                unified += UnifiedLine(DiffKind.REMOVED, e.text, e.number, null, if (idx < paired) delHighlights[idx] else emptyList())
            }
            for ((idx, e) in ins.withIndex()) {
                unified += UnifiedLine(DiffKind.ADDED, e.text, null, e.number, if (idx < paired) insHighlights[idx] else emptyList())
            }
            for (p in 0 until paired) {
                side += SideRow(
                    DiffKind.CHANGED,
                    SideCell(del[p].number, del[p].text, delHighlights[p]),
                    SideCell(ins[p].number, ins[p].text, insHighlights[p]),
                )
            }
            for (p in paired until del.size) side += SideRow(DiffKind.REMOVED, SideCell(del[p].number, del[p].text), null)
            for (p in paired until ins.size) side += SideRow(DiffKind.ADDED, null, SideCell(ins[p].number, ins[p].text))
            changed += paired
            removed += del.size - paired
            added += ins.size - paired
        }

        for ((i, j) in pairs) {
            flush(i, j)
            unified += UnifiedLine(DiffKind.EQUAL, a[i].text, a[i].number, b[j].number)
            side += SideRow(DiffKind.EQUAL, SideCell(a[i].number, a[i].text), SideCell(b[j].number, b[j].text))
            unchanged++
            ia = i + 1
            ib = j + 1
        }
        flush(a.size, b.size)
        return DiffResult(unified, side, DiffStats(added, removed, changed, unchanged), approximate)
    }

    // ---------------------------------------------------------------- intra-line

    private val TOKEN_REGEX = Regex("\\s+|[A-Za-z0-9_]+|.")

    private class Token(val start: Int, val end: Int, val text: String)

    private fun tokenize(s: String): List<Token> =
        TOKEN_REGEX.findAll(s).map { Token(it.range.first, it.range.last + 1, it.value) }.toList()

    /** Highlights of the changed tokens of an old/new pair; both empty when the lines are too different. */
    fun intraLine(old: String, new: String, ignoreWhitespace: Boolean = false): Pair<List<TextSpan>, List<TextSpan>> {
        val ta = tokenize(old)
        val tb = tokenize(new)
        if (ta.isEmpty() || tb.isEmpty()) return emptyList<TextSpan>() to emptyList()
        val ids = HashMap<String, Int>()
        fun key(t: Token) = if (ignoreWhitespace && t.text.isBlank()) " " else t.text
        val ak = IntArray(ta.size) { ids.getOrPut(key(ta[it])) { ids.size } }
        val bk = IntArray(tb.size) { ids.getOrPut(key(tb[it])) { ids.size } }
        val matches = myers(ak, bk) ?: return emptyList<TextSpan>() to emptyList()
        val matchedA = BooleanArray(ta.size)
        val matchedB = BooleanArray(tb.size)
        var meaningful = 0
        for ((i, j) in matches) {
            matchedA[i] = true; matchedB[j] = true
            if (ta[i].text.isNotBlank()) meaningful++
        }
        val totalMeaningful = maxOf(ta.count { it.text.isNotBlank() }, tb.count { it.text.isNotBlank() })
        if (totalMeaningful > 0 && meaningful * 10 < totalMeaningful * 3) return emptyList<TextSpan>() to emptyList()
        return spans(ta, matchedA, ignoreWhitespace) to spans(tb, matchedB, ignoreWhitespace)
    }

    private fun spans(tokens: List<Token>, matched: BooleanArray, ignoreWhitespace: Boolean): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        var curStart = -1
        var curEnd = -1
        for ((idx, t) in tokens.withIndex()) {
            val changed = !matched[idx] && !(ignoreWhitespace && t.text.isBlank())
            if (changed) {
                if (curStart == -1) curStart = t.start
                curEnd = t.end
            } else if (curStart != -1) {
                out += TextSpan(curStart, curEnd)
                curStart = -1
            }
        }
        if (curStart != -1) out += TextSpan(curStart, curEnd)
        return out
    }
}
