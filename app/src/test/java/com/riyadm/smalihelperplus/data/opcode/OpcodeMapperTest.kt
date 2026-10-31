package com.riyadm.smalihelperplus.data.opcode

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.database.entity.OpcodeEntity
import kotlinx.serialization.json.Json
import org.junit.Test

class OpcodeMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun entity(operands: String, refs: String, java: String? = "x = y;") = OpcodeEntity(
        name = "move", opcode = 1, category = "move", format = "12x", syntax = "move vA, vB",
        operandsJson = operands, summary = "s", description = "d", technical = "t", example = "e",
        exampleExplanation = "ee", javaEquivalent = java, kotlinEquivalent = " ", registerUsage = "r", minApi = 1,
        dexVersion = "035", compatibility = "c", commonMistakes = listOf("m"), related = listOf("move-wide"),
        referencesJson = refs, tags = listOf("t1"),
    )

    @Test
    fun decodesOperandsAndReferences() {
        val op = entity(
            """[{"name":"vA","description":"destination"},{"name":"vB","description":"source"}]""",
            """[{"title":"Spec","url":"https://source.android.com/"}]""",
        ).toDomain(json)
        assertThat(op.operands.map { it.name }).containsExactly("vA", "vB").inOrder()
        assertThat(op.references.single().url).isEqualTo("https://source.android.com/")
        assertThat(op.javaEquivalent).isEqualTo("x = y;")
        assertThat(op.kotlinEquivalent).isNull() // blank is treated as absent
        assertThat(op.related).containsExactly("move-wide")
        assertThat(op.hexValue).isEqualTo("0x01")
    }

    @Test
    fun malformedOrBlankJsonDegradesToEmpty() {
        val op = entity("not json", "").toDomain(json)
        assertThat(op.operands).isEmpty()
        assertThat(op.references).isEmpty()
    }
}
