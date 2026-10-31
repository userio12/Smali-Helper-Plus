package com.riyadm.smalihelperplus.core.content

import com.riyadm.smalihelperplus.domain.model.OpcodeCategory

/** Validates content bundles before they are written to the database. Pure Kotlin so it is unit-testable. */
object ContentValidator {

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val errors: List<String>) : Result
    }

    private val FORMAT_REGEX = Regex("^[0-9][0-9r][a-z]{1,3}$|^(packed-switch|sparse-switch|fill-array-data)-payload$")
    private val LEVELS = setOf("beginner", "intermediate", "advanced")
    private val SECTION_TYPES = setOf("text", "code", "tip", "warning", "java")

    fun validateOpcodes(bundle: OpcodeBundleDto): Result {
        val errors = mutableListOf<String>()
        if (bundle.schemaVersion != CONTENT_SCHEMA_VERSION) errors += "Unsupported schemaVersion ${bundle.schemaVersion}"
        if (bundle.contentVersion < 1) errors += "contentVersion must be >= 1"
        if (bundle.opcodes.size < 200) errors += "Opcode bundle looks truncated (${bundle.opcodes.size} entries)"
        val seen = HashSet<String>()
        bundle.opcodes.forEach { op ->
            val id = op.name.ifBlank { "<blank>" }
            if (op.name.isBlank()) errors += "Opcode with blank name"
            if (!seen.add(op.name)) errors += "Duplicate opcode $id"
            if (OpcodeCategory.fromId(op.category) == null) errors += "$id: unknown category ${op.category}"
            if (op.opcode !in 0x00..0xff && op.opcode !in setOf(0x0100, 0x0200, 0x0300)) errors += "$id: invalid opcode value ${op.opcode}"
            if (!FORMAT_REGEX.matches(op.format)) errors += "$id: invalid format ${op.format}"
            if (op.syntax.isBlank() || op.summary.isBlank() || op.description.isBlank()) errors += "$id: missing text"
            if (op.minApi !in 1..100) errors += "$id: invalid minApi ${op.minApi}"
            op.references.forEach { if (!it.url.startsWith("https://")) errors += "$id: non-HTTPS reference ${it.url}" }
        }
        return if (errors.isEmpty()) Result.Valid else Result.Invalid(errors)
    }

    fun validateLessons(bundle: LessonBundleDto): Result {
        val errors = mutableListOf<String>()
        if (bundle.schemaVersion != CONTENT_SCHEMA_VERSION) errors += "Unsupported schemaVersion ${bundle.schemaVersion}"
        if (bundle.contentVersion < 1) errors += "contentVersion must be >= 1"
        if (bundle.lessons.isEmpty()) errors += "No lessons"
        val ids = HashSet<String>()
        bundle.lessons.forEach { l ->
            if (l.id.isBlank() || l.title.isBlank()) errors += "Lesson needs an id and title"
            if (l.estimatedMinutes !in 1..1440) errors += "${l.id}: invalid duration"
            val quizIds = HashSet<String>()
            if (!ids.add(l.id)) errors += "Duplicate lesson ${l.id}"
            if (l.level !in LEVELS) errors += "${l.id}: invalid level ${l.level}"
            if (l.sections.isEmpty()) errors += "${l.id}: no sections"
            l.sections.forEach { s -> if (s.type !in SECTION_TYPES) errors += "${l.id}: invalid section type ${s.type}" }
            l.quiz.forEach { q ->
                if (q.id.isBlank() || !quizIds.add(q.id)) errors += "${l.id}: invalid or duplicate quiz id"
                if (q.options.size < 2) errors += "${l.id}/${q.id}: needs at least 2 options"
                if (q.correctIndex !in q.options.indices) errors += "${l.id}/${q.id}: correctIndex out of range"
            }
        }
        val cardIds = HashSet<String>()
        bundle.flashcards.forEach {
            if (it.id.isBlank() || it.front.isBlank() || it.back.isBlank()) errors += "Flashcard needs id, front and back"
            if (!cardIds.add(it.id)) errors += "Duplicate flashcard ${it.id}"
        }
        return if (errors.isEmpty()) Result.Valid else Result.Invalid(errors)
    }

    fun validateSnippets(bundle: SnippetBundleDto): Result {
        val errors = mutableListOf<String>()
        if (bundle.schemaVersion != CONTENT_SCHEMA_VERSION) errors += "Unsupported schemaVersion ${bundle.schemaVersion}"
        if (bundle.contentVersion < 1) errors += "contentVersion must be >= 1"
        if (bundle.snippets.isEmpty()) errors += "No snippets"
        val ids = HashSet<String>()
        bundle.snippets.forEach {
            if (it.id.isBlank() || it.title.isBlank() || it.collection.isBlank()) errors += "Snippet needs id, title and collection"
            if (!ids.add(it.id)) errors += "Duplicate snippet ${it.id}"
            if (it.code.isBlank()) errors += "${it.id}: empty code"
        }
        return if (errors.isEmpty()) Result.Valid else Result.Invalid(errors)
    }
}
