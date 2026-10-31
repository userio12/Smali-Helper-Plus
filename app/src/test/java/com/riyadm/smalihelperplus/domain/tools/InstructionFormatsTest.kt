package com.riyadm.smalihelperplus.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InstructionFormatsTest {

    private val officialIds = listOf(
        "00x", "10x", "12x", "11n", "11x", "10t", "20t", "20bc", "22x", "21t", "21s", "21h", "21c", "23x", "22b", "22t",
        "22s", "22c", "22cs", "30t", "32x", "31i", "31t", "31c", "35c", "35ms", "35mi", "3rc", "3rms", "3rmi", "45cc", "4rcc", "51l",
    )

    @Test fun containsTheOfficialFormatList() {
        assertThat(InstructionFormats.all.map { it.id }).containsExactlyElementsIn(officialIds).inOrder()
    }

    @Test fun idsAreUnique() {
        assertThat(InstructionFormats.all.map { it.id }.toSet()).hasSize(InstructionFormats.all.size)
    }

    @Test fun everyCodeUnitIsSixteenBits() {
        for (f in InstructionFormats.all) {
            for ((i, word) in f.words.withIndex()) {
                assertThat(word.sumOf { it.bits }).isEqualTo(16)
                if (i == 0) assertThat(word.any { it.isOpcode }).isTrue()
            }
        }
    }

    @Test fun codeUnitCountMatchesFirstDigitOfId() {
        for (f in InstructionFormats.all) {
            if (f.id == "00x") continue
            assertThat(f.codeUnits).isEqualTo(f.id[0].digitToInt())
            assertThat(f.byteSize).isEqualTo(f.codeUnits * 2)
        }
    }

    @Test fun optimizerOnlyFormats() {
        val only = InstructionFormats.all.filter { it.optimizerOnly }.map { it.id }
        assertThat(only).containsExactly("00x", "20bc", "22cs", "35ms", "35mi", "3rms", "3rmi")
        for (id in only) assertThat(InstructionFormats.byId(id)!!.notes).isNotEmpty()
    }

    @Test fun regularFormatsHaveExamples() {
        for (f in InstructionFormats.all.filter { !it.optimizerOnly }) {
            assertThat(f.examples).isNotEmpty()
        }
    }

    @Test fun layoutParsing() {
        val cells = InstructionFormats.parseLayout("B|A|op CCCC")
        assertThat(cells).hasSize(2)
        assertThat(cells[0].map { it.label }).containsExactly("B", "A", "op").inOrder()
        assertThat(cells[0].map { it.bits }).containsExactly(4, 4, 8).inOrder()
        assertThat(cells[1].single().bits).isEqualTo(16)
        val big = InstructionFormats.parseLayout("AA|op BBBBlo BBBB BBBB BBBBhi")
        assertThat(big).hasSize(5)
        assertThat(InstructionFormats.byId("51l")!!.codeUnits).isEqualTo(5)
    }

    @Test fun knownLayoutsMatchOfficialNotation() {
        assertThat(InstructionFormats.byId("12x")!!.layout).isEqualTo("B|A|op")
        assertThat(InstructionFormats.byId("23x")!!.layout).isEqualTo("AA|op CC|BB")
        assertThat(InstructionFormats.byId("35c")!!.layout).isEqualTo("A|G|op BBBB F|E|D|C")
        assertThat(InstructionFormats.byId("45cc")!!.layout).isEqualTo("A|G|op BBBB F|E|D|C HHHH")
        assertThat(InstructionFormats.byId("4rcc")!!.layout).isEqualTo("AA|op BBBB CCCC HHHH")
        assertThat(InstructionFormats.byId("3rc")!!.syntax.first()).isEqualTo("op {vCCCC .. vNNNN}, meth@BBBB")
    }

    @Test fun explainIdDecodesLetters() {
        assertThat(InstructionFormats.explainId("21t")).isEqualTo("2 code units, up to 1 register, a branch target")
        assertThat(InstructionFormats.explainId("3rc")).contains("range of registers")
        assertThat(InstructionFormats.explainId("35ms")).contains("static-linking")
        assertThat(InstructionFormats.explainId("45cc")).contains("second constant")
        assertThat(InstructionFormats.explainId("zz")).isNull()
        assertThat(InstructionFormats.explainId("1xx")).isNull()
    }

    @Test fun everyFormatHasText() {
        for (f in InstructionFormats.all) {
            assertThat(f.description).isNotEmpty()
            assertThat(f.idMeaning).isNotEmpty()
            assertThat(f.syntax).isNotEmpty()
        }
    }
}
