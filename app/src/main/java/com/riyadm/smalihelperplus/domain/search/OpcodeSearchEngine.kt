package com.riyadm.smalihelperplus.domain.search

import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.domain.model.OpcodeCategory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The part of an opcode a search term matched. [label] is a short human-readable description of that field. */
enum class MatchField(val label: String) {
    NAME("name"),
    CATEGORY("category"),
    FORMAT("format"),
    TAG("tag"),
    SYNTAX("syntax"),
    SUMMARY("summary"),
    DESCRIPTION("description"),
    TECHNICAL("technical details"),
    EXAMPLE("example"),
}

/** A short excerpt of a non-name/summary field that explains why an opcode matched, with ranges to highlight. */
data class MatchSnippet(val field: MatchField, val text: String, val ranges: List<IntRange>)

data class SearchResult(
    val opcode: Opcode,
    val score: Int,
    /** Field of the strongest match. */
    val matchedField: MatchField,
    /** Ranges (indices into [Opcode.name]) to highlight. */
    val nameRanges: List<IntRange>,
    /** Ranges (indices into [Opcode.summary]) to highlight. */
    val summaryRanges: List<IntRange>,
    /** Excerpt for matches outside name/summary (e.g. "in example"), else null. */
    val snippet: MatchSnippet?,
)

/**
 * Offline, in-memory search over opcode documentation. Construct it once from the loaded opcodes (the index is built
 * eagerly in the constructor) and call [search] for every query - it is cheap enough to run on each keystroke.
 *
 * Matching: every whitespace separated term must match somewhere (AND). Name matches rank
 * exact > prefix > name-token exact > name-token prefix > substring > typo (Damerau-Levenshtein), followed by format id,
 * category, tags, syntax, summary, description, technical text and example code. A term's score is its strongest field
 * plus a small share of its other matching fields; a query's score is the sum over its terms.
 */
class OpcodeSearchEngine(opcodes: List<Opcode>) {

    private class Tok(val text: String, val start: Int) {
        val end: Int get() = start + text.length
    }

    private class Entry(val opcode: Opcode) {
        val name = lc(opcode.name)
        val tokens: List<Tok> = buildList {
            var i = 0
            while (i < name.length) {
                if (name[i] == '-' || name[i] == '/' || name[i] == '_') {
                    i++
                    continue
                }
                var j = i
                while (j < name.length && name[j] != '-' && name[j] != '/' && name[j] != '_') j++
                add(Tok(name.substring(i, j), i))
                i = j
            }
        }
        val categoryText: String = lc(
            OpcodeCategory.fromId(opcode.category)?.let { "${it.id} ${it.title}" } ?: opcode.category,
        )
        val categoryTitle: String = OpcodeCategory.fromId(opcode.category)?.title ?: opcode.category
        val format = lc(opcode.format)
        val syntax = lc(opcode.syntax)
        val summary = lc(opcode.summary)
        val description = lc(opcode.description)
        val technical = lc(opcode.technical)
        val example = lc(opcode.example)
        val tags: List<String> = opcode.tags.map { lc(it) }
    }

    private class FieldHit(val field: MatchField, val score: Int, val index: Int, val length: Int)

    private class TermEval(
        val score: Int,
        val field: MatchField,
        val nameRanges: List<IntRange>,
        val summaryRanges: List<IntRange>,
        val snippet: MatchSnippet?,
    )

    private val entries: List<Entry> = opcodes.map { Entry(it) }

    /**
     * Distinct instruction format ids present in the data, in the order of the Dalvik format table: by size in code
     * units (first character), then by id, so 3rc sits next to 35c and 4rcc next to 45cc.
     */
    val formats: List<String> = opcodes.map { it.format }.distinct()
        .sortedWith(compareBy({ it.firstOrNull()?.digitToIntOrNull() ?: Int.MAX_VALUE }, { it }))

    /** Distinct minimum API levels present in the data, ascending. */
    val apiLevels: List<Int> = opcodes.map { it.minApi }.distinct().sorted()

    val size: Int get() = entries.size

    private val vocabulary: List<String> by lazy {
        val out = LinkedHashSet<String>()
        entries.forEach { e ->
            out += e.name
            e.tokens.forEach { out += it.text }
        }
        entries.forEach { e ->
            e.tags.forEach { tag -> tag.split(' ', '-', '/', '_').filter { it.length >= 3 }.forEach { out += it } }
            e.categoryText.split(' ', '_').filter { it.length >= 3 }.forEach { out += it }
        }
        out.toList()
    }

    // ------------------------------------------------------------------------------------------------ search

    /**
     * Searches the index. With a blank [query] and active [filters] every opcode that passes the filters is returned in
     * opcode-value order; with a blank query and no filters the result is empty.
     *
     * [ftsHits] optionally carries the names the Room FTS4 table matched for the same query; opcodes that matched in
     * long text and are also an FTS hit get a small ranking bonus.
     */
    fun search(
        query: String,
        filters: SearchFilters = SearchFilters(),
        ftsHits: Set<String> = emptySet(),
        limit: Int = Int.MAX_VALUE,
    ): List<SearchResult> {
        val terms = tokenize(query)
        val candidates = if (filters.isActive) entries.filter { filters.matches(it.opcode) } else entries
        if (terms.isEmpty()) {
            if (!filters.isActive) return emptyList()
            return candidates.sortedWith(compareBy({ it.opcode.opcode }, { it.opcode.name })).take(limit).map {
                SearchResult(it.opcode, 0, MatchField.NAME, emptyList(), emptyList(), null)
            }
        }

        val results = ArrayList<SearchResult>()
        for (entry in candidates) {
            val evals = ArrayList<TermEval>(terms.size)
            var total = 0
            var ok = true
            for (term in terms) {
                val eval = evaluate(entry, term)
                if (eval == null) {
                    ok = false
                    break
                }
                evals += eval
                total += eval.score
            }
            if (!ok) continue
            val best = evals.maxBy { it.score }
            if (terms.size > 1 && evals.all { it.field == MatchField.NAME }) total += 100
            if (entry.opcode.name in ftsHits && best.field.ordinal >= MatchField.SUMMARY.ordinal) total += 20
            val snippet = evals.filter { it.snippet != null }.maxByOrNull { it.score }?.snippet
            results += SearchResult(
                opcode = entry.opcode,
                score = total,
                matchedField = best.field,
                nameRanges = mergeRanges(evals.flatMap { it.nameRanges }),
                summaryRanges = mergeRanges(evals.flatMap { it.summaryRanges }),
                snippet = snippet,
            )
        }
        results.sortWith(compareByDescending<SearchResult> { it.score }.thenBy { it.opcode.opcode }.thenBy { it.opcode.name })
        return if (results.size > limit) results.subList(0, limit).toList() else results
    }

    /** Scores one term against one opcode, or null if the term matches nothing. */
    private fun evaluate(e: Entry, t: String): TermEval? {
        val hits = ArrayList<FieldHit>(6)
        var nameScore = 0
        var nameRanges: List<IntRange> = emptyList()
        matchName(e, t)?.let { (score, ranges) ->
            nameScore = score
            nameRanges = ranges
            hits += FieldHit(MatchField.NAME, score, ranges.first().first, ranges.first().last - ranges.first().first + 1)
        }

        // Format ids ("35c", "22c").
        if (e.format == t) {
            hits += FieldHit(MatchField.FORMAT, 500, 0, t.length)
        } else if (t.length >= 2 && e.format.startsWith(t)) {
            hits += FieldHit(MatchField.FORMAT, 300, 0, t.length)
        }

        if (t.length >= 2) {
            wordStartOrAny(e.categoryText, t)?.let { (idx, word) ->
                hits += FieldHit(MatchField.CATEGORY, if (word) 280 else 150, idx, t.length)
            }
        }

        // Tags.
        for ((i, tag) in e.tags.withIndex()) {
            val score = when {
                tag == t -> 260
                t.length >= 2 && tag.startsWith(t) -> 220
                t.length >= 3 && tag.contains(t) -> 150
                else -> 0
            }
            if (score > 0) {
                hits += FieldHit(MatchField.TAG, score, i, t.length)
                break
            }
        }

        var summaryRanges: List<IntRange> = emptyList()
        if (t.length >= 2) {
            wordStartOrAny(e.syntax, t)?.let { (idx, word) -> hits += FieldHit(MatchField.SYNTAX, if (word) 200 else 150, idx, t.length) }
            wordStartOrAny(e.summary, t)?.let { (idx, word) ->
                hits += FieldHit(MatchField.SUMMARY, if (word) 180 else 130, idx, t.length)
                summaryRanges = findAll(e.summary, t)
            }
            wordStartOrAny(e.description, t)?.let { (idx, word) -> hits += FieldHit(MatchField.DESCRIPTION, if (word) 100 else 70, idx, t.length) }
            wordStartOrAny(e.example, t)?.let { (idx, word) -> hits += FieldHit(MatchField.EXAMPLE, if (word) 90 else 60, idx, t.length) }
            wordStartOrAny(e.technical, t)?.let { (idx, word) -> hits += FieldHit(MatchField.TECHNICAL, if (word) 60 else 40, idx, t.length) }
        }

        if (hits.isEmpty()) return null
        val best = hits.maxBy { it.score }
        val rest = hits.sumOf { it.score } - best.score
        val snippet = if (best.field == MatchField.NAME || best.field == MatchField.SUMMARY) null else snippetFor(e, best, t)
        return TermEval(best.score + rest / 10, best.field, if (nameScore > 0) nameRanges else emptyList(), summaryRanges, snippet)
    }

    private fun matchName(e: Entry, t: String): Pair<Int, List<IntRange>>? {
        val n = e.name
        if (n == t) return 1000 to listOf(0 until n.length)
        if (n.startsWith(t)) return (800 - min(n.length - t.length, 50)) to listOf(0 until t.length)
        for ((i, tok) in e.tokens.withIndex()) {
            if (tok.text == t) return (700 - i * 5) to listOf(tok.start until tok.end)
        }
        for ((i, tok) in e.tokens.withIndex()) {
            if (tok.text.startsWith(t)) return (600 - i * 5) to listOf(tok.start until tok.start + t.length)
        }
        val idx = n.indexOf(t)
        if (idx >= 0) return (400 - min(idx, 50)) to findAll(n, t)

        // Typo tolerance.
        val maxTypos = TextDistance.maxTypos(t.length)
        if (maxTypos == 0) return null
        var bestScore = 0
        var bestRange: IntRange? = null
        val whole = TextDistance.damerauLevenshtein(t, n, maxTypos)
        if (whole <= maxTypos) {
            bestScore = 340 - 60 * whole
            bestRange = 0 until n.length
        }
        for ((i, tok) in e.tokens.withIndex()) {
            if (abs(tok.text.length - t.length) > maxTypos) continue
            val d = TextDistance.damerauLevenshtein(t, tok.text, maxTypos)
            if (d <= maxTypos) {
                val s = 290 - 50 * d - 5 * i
                if (s > bestScore) {
                    bestScore = s
                    bestRange = tok.start until tok.end
                }
            }
        }
        if (t.length >= 5) {
            for (k in -1..1) {
                val len = t.length + k
                if (len < 1 || len >= n.length) continue
                val d = TextDistance.damerauLevenshtein(t, n.substring(0, len), maxTypos)
                if (d <= maxTypos) {
                    val s = 270 - 50 * d
                    if (s > bestScore) {
                        bestScore = s
                        bestRange = 0 until len
                    }
                }
            }
        }
        return if (bestRange != null) bestScore to listOf(bestRange) else null
    }

    // ------------------------------------------------------------------------------------------------ snippets

    private fun snippetFor(e: Entry, hit: FieldHit, t: String): MatchSnippet? {
        val o = e.opcode
        return when (hit.field) {
            MatchField.CATEGORY -> {
                val title = e.categoryTitle
                MatchSnippet(MatchField.CATEGORY, title, findAll(lc(title), t).ifEmpty { listOf(0 until title.length) })
            }
            MatchField.FORMAT -> MatchSnippet(MatchField.FORMAT, o.format, listOf(0 until min(t.length, o.format.length)))
            MatchField.TAG -> {
                val tag = o.tags.getOrNull(hit.index) ?: return null
                MatchSnippet(MatchField.TAG, tag, findAll(lc(tag), t).ifEmpty { listOf(0 until tag.length) })
            }
            MatchField.SYNTAX -> excerpt(MatchField.SYNTAX, o.syntax, hit.index, t, wholeLine = true)
            MatchField.DESCRIPTION -> excerpt(MatchField.DESCRIPTION, o.description, hit.index, t, wholeLine = false)
            MatchField.TECHNICAL -> excerpt(MatchField.TECHNICAL, o.technical, hit.index, t, wholeLine = false)
            MatchField.EXAMPLE -> excerpt(MatchField.EXAMPLE, o.example, hit.index, t, wholeLine = true)
            else -> null
        }
    }

    private fun excerpt(field: MatchField, original: String, matchIndex: Int, term: String, wholeLine: Boolean): MatchSnippet? {
        if (matchIndex < 0 || matchIndex > original.length) return null
        var from = 0
        var to = original.length
        if (wholeLine) {
            from = original.lastIndexOf('\n', matchIndex - 1).let { if (it < 0) 0 else it + 1 }
            to = original.indexOf('\n', matchIndex).let { if (it < 0) original.length else it }
        }
        while (from < matchIndex && original[from].isWhitespace()) from++
        val window = 90
        var start = from
        var end = to
        if (end - start > window) {
            start = max(from, matchIndex - 30)
            end = min(to, start + window)
            if (end - start < window) start = max(from, end - window)
        }
        val body = original.substring(start, end).replace('\n', ' ').trimEnd()
        val text = (if (start > from) "…" else "") + body + (if (end < to) "…" else "")
        val shift = if (start > from) 1 else 0
        val lower = lc(text)
        val ranges = findAll(lower, term).ifEmpty {
            val rel = matchIndex - start + shift
            if (rel in 0 until text.length) listOf(rel until min(text.length, rel + term.length)) else emptyList()
        }
        return MatchSnippet(field, text, ranges)
    }

    // ------------------------------------------------------------------------------------------------ suggestions

    /**
     * Name completions for the term currently being typed (the last term of [query]): names starting with it first,
     * then names with a token starting with it. Shorter and lower-valued instructions first. The exact name is omitted.
     */
    fun suggestions(query: String, limit: Int = 5): List<String> {
        val term = tokenize(query).lastOrNull() ?: return emptyList()
        val byPrefix = entries.filter { it.name.startsWith(term) && it.name != term }
        val pool = byPrefix.ifEmpty {
            entries.filter { e -> e.name != term && e.tokens.any { it.text.startsWith(term) } }
        }
        return pool.sortedWith(compareBy({ it.name.length }, { it.opcode.opcode }, { it.name }))
            .take(limit).map { it.opcode.name }
    }

    /**
     * "Did you mean" for a query that produced no results: replaces each unrecognised term with the closest known
     * mnemonic, name part, tag or category word. Returns null if no sensible correction exists.
     */
    fun didYouMean(query: String): String? {
        val terms = tokenize(query)
        if (terms.isEmpty()) return null
        var changed = false
        val corrected = terms.map { term ->
            val known = vocabulary.any { it.startsWith(term) } || entries.any { it.name.contains(term) }
            if (known) return@map term
            val maxD = TextDistance.maxSuggestionDistance(term.length)
            if (maxD == 0) return@map term
            var best: String? = null
            var bestD = maxD + 1
            for (word in vocabulary) {
                if (abs(word.length - term.length) > maxD) continue
                val d = TextDistance.damerauLevenshtein(term, word, maxD)
                if (d < bestD || (d == bestD && best != null && word.length < best.length)) {
                    best = word
                    bestD = d
                }
            }
            if (best != null && bestD <= maxD) {
                changed = true
                best
            } else {
                term
            }
        }
        return if (changed) corrected.joinToString(" ") else null
    }

    companion object {
        private const val MAX_TERMS = 8

        /** Lowercased, de-duplicated search terms of [query] (whitespace separated, at most 8). */
        fun tokenize(query: String): List<String> =
            query.lowercase(Locale.ROOT).split(Regex("\\s+"))
                .map { it.trim(',', ';') }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_TERMS)

        /** Lowercases without changing the string length (so indices stay valid for the original text). */
        private fun lc(s: String): String {
            val l = s.lowercase(Locale.ROOT)
            return if (l.length == s.length) l else s
        }

        private fun isWordStart(text: String, index: Int): Boolean = index == 0 || !text[index - 1].isLetterOrDigit()

        /** Index of the first word-start occurrence (true) or else the first occurrence anywhere (false); null if absent. */
        private fun wordStartOrAny(text: String, term: String): Pair<Int, Boolean>? {
            var first = -1
            var from = 0
            while (true) {
                val idx = text.indexOf(term, from)
                if (idx < 0) break
                if (first < 0) first = idx
                if (isWordStart(text, idx)) return idx to true
                from = idx + 1
            }
            return if (first >= 0) first to false else null
        }

        private fun findAll(text: String, term: String): List<IntRange> {
            if (term.isEmpty()) return emptyList()
            val out = ArrayList<IntRange>()
            var from = 0
            while (true) {
                val idx = text.indexOf(term, from)
                if (idx < 0) break
                out += idx until idx + term.length
                from = idx + term.length
            }
            return out
        }

        /** Sorts and merges overlapping or touching ranges. */
        fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
            if (ranges.size <= 1) return ranges
            val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
            val out = ArrayList<IntRange>()
            for (r in sorted) {
                val last = out.lastOrNull()
                if (last != null && r.first <= last.last + 1) {
                    out[out.lastIndex] = last.first..max(last.last, r.last)
                } else {
                    out += r
                }
            }
            return out
        }
    }
}
