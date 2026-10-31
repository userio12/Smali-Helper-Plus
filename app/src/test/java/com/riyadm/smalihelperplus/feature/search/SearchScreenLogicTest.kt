package com.riyadm.smalihelperplus.feature.search

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchScreenLogicTest {
    @Test
    fun replaceLastTermCompletesOnlyTheLastWord() {
        assertThat(replaceLastTerm("inv", "invoke-static")).isEqualTo("invoke-static")
        assertThat(replaceLastTerm("static inv", "invoke-static")).isEqualTo("static invoke-static")
        assertThat(replaceLastTerm("inv  ", "invoke-static")).isEqualTo("invoke-static")
    }

    @Test
    fun highlightedTextClampsRanges() {
        val text = highlightedText("const/4", listOf(0..4, 5..50, -3..-1), Color.Yellow, Color.Black)
        assertThat(text.text).isEqualTo("const/4")
        assertThat(text.spanStyles.map { it.start to it.end }).containsExactly(0 to 5, 5 to 7)
        assertThat(highlightedText("abc", emptyList(), Color.Yellow, Color.Black).spanStyles).isEmpty()
    }
}
