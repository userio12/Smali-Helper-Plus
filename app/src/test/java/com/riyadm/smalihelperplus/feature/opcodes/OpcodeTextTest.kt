package com.riyadm.smalihelperplus.feature.opcodes

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.content.OperandDto
import com.riyadm.smalihelperplus.domain.search.TestOpcodes
import org.junit.Test

class OpcodeTextTest {
    @Test
    fun summaryContainsKeyFacts() {
        val op = TestOpcodes.op(
            "const/4", 0x12, "constant", "11n", "const/4 vA, #+B", "Load a 4-bit literal.",
            example = "const/4 v0, 0x1", java = "int v0 = 1;",
        ).copy(operands = listOf(OperandDto("vA", "destination")), commonMistakes = listOf("Literal out of range"))
        val text = OpcodeText.summary(op)
        assertThat(text).contains("const/4 (0x12) - Constants")
        assertThat(text).contains("Format 11n | API 1+ | DEX 035")
        assertThat(text).contains("const/4 vA, #+B")
        assertThat(text).contains("vA: destination")
        assertThat(text).contains("const/4 v0, 0x1")
        assertThat(text).contains("educational pseudocode")
        assertThat(text).contains("Literal out of range")
        assertThat(text).doesNotContain("Kotlin")
    }

    @Test
    fun payloadValuesUseFourDigitHex() {
        assertThat(TestOpcodes.op("packed-switch-payload", 0x0100, "advanced", "x").hexValue).isEqualTo("0x0100")
        assertThat(TestOpcodes.op("nop", 0, "advanced", "10x").hexValue).isEqualTo("0x00")
        assertThat(TestOpcodes.op("x", 0xfa, "advanced", "10x").hexValue).isEqualTo("0xFA")
    }

    @Test
    fun onlyWebUrlsAreOpenable() {
        assertThat(OpcodeText.isOpenableUrl("https://source.android.com/docs")).isTrue()
        assertThat(OpcodeText.isOpenableUrl(" HTTP://example.com")).isTrue()
        assertThat(OpcodeText.isOpenableUrl("intent://scan/#Intent;end")).isFalse()
        assertThat(OpcodeText.isOpenableUrl("file:///etc/passwd")).isFalse()
        assertThat(OpcodeText.isOpenableUrl("javascript:alert(1)")).isFalse()
    }
}
