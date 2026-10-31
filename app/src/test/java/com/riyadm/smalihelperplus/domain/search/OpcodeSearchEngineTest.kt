package com.riyadm.smalihelperplus.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpcodeSearchEngineTest {
    private val engine = OpcodeSearchEngine(TestOpcodes.all)

    private fun names(query: String, filters: SearchFilters = SearchFilters()) =
        engine.search(query, filters).map { it.opcode.name }

    @Test
    fun blankQueryWithoutFiltersIsEmpty() {
        assertThat(engine.search("")).isEmpty()
        assertThat(engine.search("   ")).isEmpty()
    }

    @Test
    fun exactNameRanksFirst() {
        assertThat(names("move").first()).isEqualTo("move")
        assertThat(names("const/4").first()).isEqualTo("const/4")
    }

    @Test
    fun rankingOrderExactPrefixTokenPrefixSubstring() {
        // "move": exact first, then prefix matches (closer in length first, then opcode value)
        val move = names("move")
        assertThat(move.take(4)).containsExactly("move", "move-wide", "move/from16", "move-result").inOrder()

        // "wide": token exact in move-wide/iget-wide ranks above description-only mentions (move/from16 summary says "wide")
        val wide = names("wide")
        assertThat(wide.indexOf("move-wide")).isLessThan(wide.indexOf("move/from16"))
        assertThat(wide.indexOf("iget-wide")).isLessThan(wide.indexOf("move/from16"))

        // "stat" is a token prefix of invoke-static; "polymorphic" does not match.
        assertThat(names("stat")).containsAtLeast("invoke-static", "invoke-static/range")

        // substring inside a token: "tatic"
        assertThat(names("tatic").take(2)).containsExactly("invoke-static", "invoke-static/range")
    }

    @Test
    fun exactBeatsPrefixBeatsTokenBeatsSubstring() {
        val list = listOf(
            TestOpcodes.op("abc", 1, "move", "10x"),
            TestOpcodes.op("abcd", 2, "move", "10x"),
            TestOpcodes.op("x-abc", 3, "move", "10x"),
            TestOpcodes.op("x-abcd", 4, "move", "10x"),
            TestOpcodes.op("xabc", 5, "move", "10x"),
        )
        val result = OpcodeSearchEngine(list).search("abc").map { it.opcode.name }
        assertThat(result).containsExactly("abc", "abcd", "x-abc", "x-abcd", "xabc").inOrder()
    }

    @Test
    fun typoToleranceMissingLetter() {
        val r = engine.search("invok-static")
        assertThat(r.first().opcode.name).isEqualTo("invoke-static")
        assertThat(r.map { it.opcode.name }).contains("invoke-static/range")
    }

    @Test
    fun typoToleranceConst4() {
        val r = engine.search("cnst/4")
        assertThat(r.first().opcode.name).isEqualTo("const/4")
    }

    @Test
    fun typoToleranceTransposition() {
        assertThat(names("retrun-void").first()).isEqualTo("return-void")
        assertThat(names("statci")).contains("invoke-static")
    }

    @Test
    fun shortTermsAreNotFuzzy() {
        // "mo" must not fuzzily match "nop"/"add-int" etc.; only real prefix/substring matches appear.
        assertThat(names("zz")).isEmpty()
        assertThat(names("nq")).isEmpty()
    }

    @Test
    fun tooManyTyposFindsNothing() {
        assertThat(names("invokstatik")).isEmpty()
    }

    @Test
    fun multiTermUsesAndSemantics() {
        val r = names("invoke static")
        assertThat(r.take(2)).containsExactly("invoke-static", "invoke-static/range")
        assertThat(r).doesNotContain("invoke-polymorphic")
        assertThat(names("invoke qqqq")).isEmpty()
        // terms may match different fields: name + format
        assertThat(names("invoke 3rc")).containsExactly("invoke-static/range")
    }

    @Test
    fun formatSearch() {
        val r = engine.search("35c")
        assertThat(r.map { it.opcode.name }).containsExactly("invoke-virtual", "invoke-static").inOrder()
        assertThat(r.all { it.matchedField == MatchField.FORMAT }).isTrue()
        assertThat(r.first().snippet?.text).isEqualTo("35c")

        assertThat(names("22c")).containsExactly("iget", "iget-wide").inOrder()
    }

    @Test
    fun categorySearch() {
        val r = engine.search("arithmetic")
        assertThat(r.map { it.opcode.name }).containsExactly("add-int", "add-long").inOrder()
        assertThat(r.first().matchedField).isEqualTo(MatchField.CATEGORY)
        // category title words
        assertThat(names("constants")).containsAtLeast("const/4", "const-string")
    }

    @Test
    fun exampleAndDescriptionAndTagMatches() {
        val example = engine.search("hello").single()
        assertThat(example.opcode.name).isEqualTo("const-string")
        assertThat(example.matchedField).isEqualTo(MatchField.EXAMPLE)
        assertThat(example.snippet!!.field).isEqualTo(MatchField.EXAMPLE)
        assertThat(example.snippet!!.text).contains("hello")
        val range = example.snippet!!.ranges.single()
        assertThat(example.snippet!!.text.substring(range.first, range.last + 1)).isEqualTo("hello")

        val description = engine.search("destination").single()
        assertThat(description.matchedField).isEqualTo(MatchField.DESCRIPTION)

        val tag = engine.search("math").single()
        assertThat(tag.opcode.name).isEqualTo("add-int")
        assertThat(tag.matchedField).isEqualTo(MatchField.TAG)
    }

    @Test
    fun nameRangesForHighlighting() {
        val exact = engine.search("const/4").first()
        assertThat(exact.nameRanges).containsExactly(0..6)

        val prefix = engine.search("move").first { it.opcode.name == "move-wide" }
        assertThat(prefix.nameRanges).containsExactly(0..3)

        val token = engine.search("static").first { it.opcode.name == "invoke-static/range" }
        assertThat(token.nameRanges).containsExactly(7..12)

        val multi = engine.search("invoke static").first { it.opcode.name == "invoke-static" }
        assertThat(multi.nameRanges).containsExactly(0..5, 7..12)

        val fuzzy = engine.search("invok-static").first()
        assertThat(fuzzy.nameRanges).containsExactly(0..12)
    }

    @Test
    fun summaryRangesForHighlighting() {
        val r = engine.search("static").first { it.opcode.name == "invoke-static" }
        val summary = r.opcode.summary
        assertThat(r.summaryRanges).isNotEmpty()
        r.summaryRanges.forEach {
            assertThat(summary.substring(it.first, it.last + 1).lowercase()).isEqualTo("static")
        }
    }

    @Test
    fun filters() {
        assertThat(names("", SearchFilters(categories = setOf("invoke"))))
            .containsExactly("invoke-virtual", "invoke-static", "invoke-static/range").inOrder()
        assertThat(names("", SearchFilters(formats = setOf("12x")))).containsExactly("move", "move-wide").inOrder()
        assertThat(names("", SearchFilters(maxApi = 1))).doesNotContain("invoke-polymorphic")
        assertThat(names("", SearchFilters(maxApi = 26))).contains("invoke-polymorphic")
        assertThat(names("", SearchFilters(hasJavaEquivalent = true)))
            .containsExactly("const/4", "iget", "invoke-virtual", "invoke-static").inOrder()
        assertThat(names("", SearchFilters(wideOnly = true)))
            .containsExactly("move-wide", "iget-wide", "add-long").inOrder()
        assertThat(names("", SearchFilters(rangeOnly = true))).containsExactly("invoke-static/range")
        // filters combine with a query and with each other
        assertThat(names("invoke", SearchFilters(rangeOnly = true, categories = setOf("invoke"))))
            .containsExactly("invoke-static/range")
        assertThat(names("add", SearchFilters(wideOnly = true))).containsExactly("add-long")
        assertThat(SearchFilters(wideOnly = true, formats = setOf("12x")).activeCount).isEqualTo(2)
        assertThat(SearchFilters().isActive).isFalse()
    }

    @Test
    fun suggestionsCompleteNames() {
        assertThat(engine.suggestions("inv")).containsExactly("invoke-static", "invoke-virtual", "invoke-polymorphic", "invoke-static/range").inOrder()
        assertThat(engine.suggestions("move-w")).containsExactly("move-wide")
        assertThat(engine.suggestions("invoke-static")).containsExactly("invoke-static/range")
        // last term is completed
        assertThat(engine.suggestions("some const")).contains("const/4")
        assertThat(engine.suggestions("")).isEmpty()
        // token prefix fallback
        assertThat(engine.suggestions("stat")).containsAtLeast("invoke-static", "invoke-static/range")
    }

    @Test
    fun didYouMeanForZeroResultTypos() {
        assertThat(names("invokstatik")).isEmpty()
        assertThat(engine.didYouMean("invokstatik")).isEqualTo("invoke-static")
        assertThat(engine.didYouMean("move-wde")).isEqualTo("move-wide")
    }

    @Test
    fun didYouMeanKeepsKnownTerms() {
        assertThat(engine.didYouMean("invoke")).isNull()
        assertThat(engine.didYouMean("invoke stattik")).isEqualTo("invoke static")
        assertThat(engine.didYouMean("")).isNull()
    }

    @Test
    fun ftsHitsBoostLongTextMatches() {
        val plain = engine.search("receiver").map { it.opcode.name }
        assertThat(plain).containsExactly("invoke-static")
        val list = listOf(
            TestOpcodes.op("aaa", 1, "move", "10x", summary = "uses a cache"),
            TestOpcodes.op("bbb", 2, "move", "10x", summary = "uses a cache"),
        )
        val e = OpcodeSearchEngine(list)
        assertThat(e.search("cache").map { it.opcode.name }).containsExactly("aaa", "bbb").inOrder()
        assertThat(e.search("cache", ftsHits = setOf("bbb")).map { it.opcode.name }).containsExactly("bbb", "aaa").inOrder()
    }

    @Test
    fun formatsAndApiLevelsAreExposed() {
        // Dalvik table order: grouped by code-unit size, so 3rc follows 35c instead of preceding 10x.
        assertThat(engine.formats).containsAtLeast("10x", "35c", "3rc").inOrder()
        assertThat(engine.formats.first()).isNotEqualTo("3rc")
        assertThat(engine.apiLevels).containsExactly(1, 26).inOrder()
    }

    @Test
    fun limitIsApplied() {
        assertThat(engine.search("move", limit = 2)).hasSize(2)
    }

    @Test
    fun mergeRanges() {
        assertThat(OpcodeSearchEngine.mergeRanges(listOf(5..7, 0..2, 3..4, 10..12))).containsExactly(0..7, 10..12).inOrder()
    }
}
