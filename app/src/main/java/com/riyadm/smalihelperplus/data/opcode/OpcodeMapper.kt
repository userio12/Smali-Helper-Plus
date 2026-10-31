package com.riyadm.smalihelperplus.data.opcode

import com.riyadm.smalihelperplus.core.content.OperandDto
import com.riyadm.smalihelperplus.core.content.ReferenceDto
import com.riyadm.smalihelperplus.core.database.entity.OpcodeEntity
import com.riyadm.smalihelperplus.domain.model.Opcode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Converts a Room entity to the domain model, decoding the JSON columns. Malformed JSON degrades to empty lists. */
fun OpcodeEntity.toDomain(json: Json): Opcode = Opcode(
    name = name,
    opcode = opcode,
    category = category,
    format = format,
    syntax = syntax,
    operands = decodeList(json, operandsJson, OperandDto.serializer()),
    summary = summary,
    description = description,
    technical = technical,
    example = example,
    exampleExplanation = exampleExplanation,
    javaEquivalent = javaEquivalent?.takeIf { it.isNotBlank() },
    kotlinEquivalent = kotlinEquivalent?.takeIf { it.isNotBlank() },
    registerUsage = registerUsage,
    minApi = minApi,
    dexVersion = dexVersion,
    compatibility = compatibility,
    commonMistakes = commonMistakes,
    related = related,
    references = decodeList(json, referencesJson, ReferenceDto.serializer()),
    tags = tags,
)

private fun <T> decodeList(json: Json, text: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
    if (text.isBlank()) {
        emptyList()
    } else {
        try {
            json.decodeFromString(ListSerializer(serializer), text)
        } catch (_: Exception) {
            emptyList()
        }
    }
