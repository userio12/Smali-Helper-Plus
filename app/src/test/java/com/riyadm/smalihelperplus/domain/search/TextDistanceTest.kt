package com.riyadm.smalihelperplus.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextDistanceTest {
    private fun d(a: String, b: String, max: Int = 3) = TextDistance.damerauLevenshtein(a, b, max)

    @Test
    fun basicEdits() {
        assertThat(d("abc", "abc")).isEqualTo(0)
        assertThat(d("abc", "abd")).isEqualTo(1) // substitution
        assertThat(d("abc", "abcd")).isEqualTo(1) // insertion
        assertThat(d("abcd", "abc")).isEqualTo(1) // deletion
        assertThat(d("ab", "ba")).isEqualTo(1) // transposition
        assertThat(d("invok-static", "invoke-static")).isEqualTo(1)
        assertThat(d("cnst/4", "const/4")).isEqualTo(1)
        assertThat(d("", "ab")).isEqualTo(2)
    }

    @Test
    fun boundIsRespected() {
        assertThat(d("kitten", "sitting", max = 2)).isEqualTo(3) // true distance 3 > 2 -> max + 1
        assertThat(d("kitten", "sitting", max = 3)).isEqualTo(3)
        assertThat(d("abc", "xyzxyz", max = 1)).isEqualTo(2)
    }

    @Test
    fun typoBudgets() {
        assertThat(TextDistance.maxTypos(2)).isEqualTo(0)
        assertThat(TextDistance.maxTypos(3)).isEqualTo(1)
        assertThat(TextDistance.maxTypos(5)).isEqualTo(1)
        assertThat(TextDistance.maxTypos(6)).isEqualTo(2)
        assertThat(TextDistance.maxTypos(20)).isEqualTo(2)
    }
}
