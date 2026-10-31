package com.riyadm.smalihelperplus.core.content

import kotlinx.serialization.Serializable

/*
 * Versioned JSON content contracts. The same DTOs are used for the bundled assets
 * (the JSON files in assets/content) and for validated remote documentation updates.
 * Increase SCHEMA_VERSION only for breaking format changes.
 */
const val CONTENT_SCHEMA_VERSION = 1

@Serializable
data class OpcodeBundleDto(
    val schemaVersion: Int,
    val contentVersion: Int,
    val generatedAt: String,
    val opcodes: List<OpcodeDto>,
)

@Serializable
data class OpcodeDto(
    /** Mnemonic exactly as written in smali, e.g. "move-wide/from16". Pseudo-instructions use e.g. "packed-switch-payload". */
    val name: String,
    /** Opcode byte value (0x00-0xff). Payload pseudo-instructions use their ident (0x0100, 0x0200, 0x0300). */
    val opcode: Int,
    /** Category id, see [OpcodeCategory]. */
    val category: String,
    /** Dalvik instruction format id, e.g. "12x", "22c", "35c", "4rcc". */
    val format: String,
    /** Smali syntax, e.g. "move vA, vB". */
    val syntax: String,
    val operands: List<OperandDto> = emptyList(),
    /** One-line summary used in lists and search results. */
    val summary: String,
    /** Plain-language explanation. */
    val description: String,
    /** Technical description (encoding, semantics, edge cases). */
    val technical: String,
    /** Real-world smali example. */
    val example: String,
    val exampleExplanation: String = "",
    val javaEquivalent: String? = null,
    val kotlinEquivalent: String? = null,
    /** Description of how registers are read/written. */
    val registerUsage: String,
    /** Minimum Android API level where the instruction is valid (1 for original instructions). */
    val minApi: Int = 1,
    /** Minimum DEX file version, e.g. "035", "038", "039". */
    val dexVersion: String = "035",
    val compatibility: String = "",
    val commonMistakes: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    val references: List<ReferenceDto> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class OperandDto(val name: String, val description: String)

@Serializable
data class ReferenceDto(val title: String, val url: String)

@Serializable
data class LessonBundleDto(
    val schemaVersion: Int,
    val contentVersion: Int,
    val generatedAt: String,
    val lessons: List<LessonDto>,
    val flashcards: List<FlashcardDto> = emptyList(),
)

@Serializable
data class LessonDto(
    val id: String,
    /** "beginner" | "intermediate" | "advanced" */
    val level: String,
    val order: Int,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int,
    val sections: List<LessonSectionDto>,
    val exercises: List<ExerciseDto> = emptyList(),
    val quiz: List<QuizQuestionDto> = emptyList(),
    val relatedOpcodes: List<String> = emptyList(),
)

@Serializable
data class LessonSectionDto(
    /** "text" | "code" | "tip" | "warning" | "java" */
    val type: String,
    val title: String? = null,
    val body: String,
)

@Serializable
data class ExerciseDto(
    val id: String,
    val prompt: String,
    val starterCode: String? = null,
    val solution: String,
    val hint: String? = null,
)

@Serializable
data class QuizQuestionDto(
    val id: String,
    val question: String,
    val code: String? = null,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
)

@Serializable
data class FlashcardDto(
    val id: String,
    val topic: String,
    val front: String,
    val back: String,
    val code: String? = null,
)

@Serializable
data class SnippetBundleDto(
    val schemaVersion: Int,
    val contentVersion: Int,
    val generatedAt: String,
    val snippets: List<SnippetDto>,
)

@Serializable
data class SnippetDto(
    val id: String,
    val title: String,
    val description: String,
    /** Collection name, e.g. "Classes", "Methods", "Strings", "Arrays", "Conditions", "Loops", "Method calls", "Exceptions". */
    val collection: String,
    val code: String,
    val tags: List<String> = emptyList(),
)
