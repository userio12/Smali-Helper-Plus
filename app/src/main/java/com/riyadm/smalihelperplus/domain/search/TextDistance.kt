package com.riyadm.smalihelperplus.domain.search

/** String distance helpers used for typo tolerance. */
object TextDistance {

    /**
     * Damerau-Levenshtein distance (optimal string alignment: insert, delete, substitute, transpose adjacent) between
     * [a] and [b], bounded by [max]. Returns `max + 1` as soon as the distance is known to exceed [max].
     */
    fun damerauLevenshtein(a: String, b: String, max: Int): Int {
        if (max < 0) return 0
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > max) return max + 1
        if (n == 0) return if (m <= max) m else max + 1
        if (m == 0) return if (n <= max) n else max + 1

        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prev2[j - 2] + 1)
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val tmp = prev2
            prev2 = prev
            prev = cur
            cur = tmp
        }
        return minOf(prev[m], max + 1)
    }

    /** Maximum edit distance tolerated for a search term of [length] characters (0 = no typo tolerance). */
    fun maxTypos(length: Int): Int = when {
        length < 3 -> 0
        length < 6 -> 1
        else -> 2
    }

    /** Wider bound used only for "did you mean" suggestions when a search found nothing. */
    fun maxSuggestionDistance(length: Int): Int = when {
        length < 3 -> 0
        length <= 3 -> 1
        length < 8 -> 2
        else -> 3
    }
}
