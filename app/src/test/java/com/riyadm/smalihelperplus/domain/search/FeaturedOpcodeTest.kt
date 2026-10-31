package com.riyadm.smalihelperplus.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeaturedOpcodeTest {
    private val opcodes = TestOpcodes.all

    @Test
    fun sameDaySameOpcode() {
        repeat(5) { day ->
            val d = 20000L + day
            assertThat(FeaturedOpcode.pick(opcodes, d)).isEqualTo(FeaturedOpcode.pick(opcodes.reversed(), d))
        }
    }

    @Test
    fun consecutiveDaysDiffer() {
        val picks = (0 until 10).map { FeaturedOpcode.pick(opcodes, 20000L + it)!!.name }
        picks.zipWithNext().forEach { (a, b) -> assertThat(a).isNotEqualTo(b) }
    }

    @Test
    fun cycleCoversEveryEligibleOpcodeOnce() {
        val eligible = opcodes.filter { it.name != "nop" && !it.name.endsWith("-payload") }
        val seen = (0 until eligible.size).map { FeaturedOpcode.pick(opcodes, 19000L + it)!!.name }
        assertThat(seen).containsExactlyElementsIn(eligible.map { it.name })
    }

    @Test
    fun skipsNopAndPayloads() {
        val names = (0 until 60).map { FeaturedOpcode.pick(opcodes, it.toLong())!!.name }
        assertThat(names).doesNotContain("nop")
        assertThat(names).doesNotContain("packed-switch-payload")
    }

    @Test
    fun handlesEmptyAndNegativeDays() {
        assertThat(FeaturedOpcode.pick(emptyList(), 1)).isNull()
        assertThat(FeaturedOpcode.pick(opcodes, -5)).isNotNull()
    }
}
