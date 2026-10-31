package com.riyadm.smalihelperplus.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQueryTest {
    @Test
    fun buildsPrefixTerms() {
        assertThat(FtsQuery.sanitize("invoke")).isEqualTo("invoke*")
        assertThat(FtsQuery.sanitize("Invoke Static")).isEqualTo("invoke* static*")
        assertThat(FtsQuery.sanitize("invoke-static/range")).isEqualTo("invoke* static* range*")
        assertThat(FtsQuery.sanitize("35c")).isEqualTo("35c*")
    }

    @Test
    fun stripsOperatorsAndQuotes() {
        assertThat(FtsQuery.sanitize("\"foo\" OR (bar) -baz NEAR/2 col:val *")).isEqualTo("foo* or* bar* baz* near* 2* col* val*")
        assertThat(FtsQuery.sanitize("a*b")).isEqualTo("a* b*")
    }

    @Test
    fun returnsNullWhenNothingSearchable() {
        assertThat(FtsQuery.sanitize("")).isNull()
        assertThat(FtsQuery.sanitize("  ---  \"\" ()")).isNull()
    }

    @Test
    fun deduplicatesAndLimitsTokens() {
        assertThat(FtsQuery.sanitize("move move")).isEqualTo("move*")
        val many = (1..20).joinToString(" ") { "t$it" }
        assertThat(FtsQuery.sanitize(many)!!.split(" ")).hasSize(8)
    }
}
