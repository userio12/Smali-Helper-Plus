package com.riyadm.smalihelperplus.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CheatSheetsAndSearchTest {

    @Test fun sheetsCoverRequestedTopics() {
        assertThat(CheatSheets.all.map { it.id }).containsAtLeast(
            "descriptors", "access", "directives", "registers", "invokes", "patterns", "iftests", "literals",
        )
        assertThat(CheatSheets.all.map { it.id }.toSet()).hasSize(CheatSheets.all.size)
    }

    @Test fun everySheetHasContent() {
        for (s in CheatSheets.all) {
            assertThat(s.summary).isNotEmpty()
            assertThat(s.blocks).isNotEmpty()
            for (b in s.blocks) {
                assertThat(b.text != null || b.table != null || b.code != null).isTrue()
                b.table?.let { t ->
                    assertThat(t.rows).isNotEmpty()
                    for (row in t.rows) assertThat(row).hasSize(t.headers.size)
                }
            }
            assertThat(s.copyText).contains(s.title)
        }
    }

    @Test fun accessFlagValuesMatchTheDexFormat() {
        val table = CheatSheets.byId("access")!!.blocks.first().table!!
        val values = table.rows.associate { it[0] to it[1] }
        assertThat(values["public"]).isEqualTo("0x1")
        assertThat(values["private"]).isEqualTo("0x2")
        assertThat(values["protected"]).isEqualTo("0x4")
        assertThat(values["static"]).isEqualTo("0x8")
        assertThat(values["final"]).isEqualTo("0x10")
        assertThat(values["native"]).isEqualTo("0x100")
        assertThat(values["interface"]).isEqualTo("0x200")
        assertThat(values["abstract"]).isEqualTo("0x400")
        assertThat(values["synthetic"]).isEqualTo("0x1000")
        assertThat(values["annotation"]).isEqualTo("0x2000")
        assertThat(values["enum"]).isEqualTo("0x4000")
        assertThat(values["constructor"]).isEqualTo("0x10000")
        assertThat(values["declared-synchronized"]).isEqualTo("0x20000")
    }

    @Test fun literalSheetAgreesWithNumberConverter() {
        val rows = CheatSheets.byId("literals")!!.blocks.first().table!!.rows.associate { it[0] to it[1] }
        assertThat(rows["const/4"]).isEqualTo("11n")
        assertThat(rows["const-wide"]).isEqualTo("51l")
        // Every instruction named in the sheet is also produced by the converter.
        val produced = (NumberConverter.constOptions(0, false) + NumberConverter.constOptions(0, true)).map { it.opcode }.toSet()
        assertThat(produced).containsExactlyElementsIn(rows.keys)
    }

    @Test fun tableCopyTextIsTabSeparated() {
        val t = CheatTable(listOf("A", "B"), listOf(listOf("1", "2")))
        assertThat(t.toPlainText()).isEqualTo("A\tB\n1\t2")
    }

    @Test fun toolSearchRequiresEveryWord() {
        assertThat(ToolSearch.matches("", "Number converter", "hex and binary")).isTrue()
        assertThat(ToolSearch.matches("hex", "Number converter", "hex and binary")).isTrue()
        assertThat(ToolSearch.matches("number hex", "Number converter", "hex and binary")).isTrue()
        assertThat(ToolSearch.matches("number diff", "Number converter", "hex and binary")).isFalse()
        assertThat(ToolSearch.matches("IEEE", "Number converter", "bits", listOf("ieee"))).isTrue()
    }

    @Test fun androidVersionsAreContiguous() {
        assertThat(AndroidVersions.all.map { it.api }).containsExactlyElementsIn((1..37).toList()).inOrder()
        assertThat(AndroidVersions.byApi(34)!!.release).isEqualTo("14")
        assertThat(AndroidVersions.label(999)).isEqualTo("API 999")
    }
}
