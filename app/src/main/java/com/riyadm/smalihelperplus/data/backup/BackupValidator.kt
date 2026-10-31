package com.riyadm.smalihelperplus.data.backup

import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.LessonStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Pure parsing + validation of backup files (JVM-testable). */
object BackupValidator {
    private const val MAX_ROWS = 100_000
    private const val MAX_ID_CHARS = 300
    private const val MAX_TITLE_CHARS = 2_000
    private const val MAX_CODE_CHARS = 500_000
    private const val MAX_TAGS = 50
    private const val MAX_TAG_CHARS = 100
    private const val MAX_MILLIS = 4_102_444_800_000L // 2100-01-01
    private const val MAX_EPOCH_DAY = 47_482L // 2100-01-01
    private val ENTITY_TYPES = setOf(BookmarkType.OPCODE, BookmarkType.LESSON, BookmarkType.SNIPPET)

    /** Json instance for reading backups: tolerant of unknown keys written by newer versions. */
    val readJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Json instance for writing backups: human-readable and stable. */
    val writeJson = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    /** Parses and validates [text]. Throws [BackupException] with a user-presentable message. */
    fun parse(text: String): BackupDto {
        if (text.length > MAX_BACKUP_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            throw BackupException("The backup file exceeds the size limit.")
        }
        val dto = try {
            readJson.decodeFromString(BackupDto.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("This file is not a Smali Helper Plus backup.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("This file is not a Smali Helper Plus backup.", e)
        }
        val errors = validate(dto)
        if (errors.isNotEmpty()) {
            throw BackupException("The backup file is invalid: ${errors.take(3).joinToString("; ")}")
        }
        return dto
    }

    /** Returns a list of problems; empty means the backup is safe to import. */
    fun validate(dto: BackupDto): List<String> {
        val errors = mutableListOf<String>()
        if (dto.formatVersion < 1) errors += "unknown format version ${dto.formatVersion}"
        if (dto.formatVersion > BACKUP_FORMAT_VERSION) {
            errors += "it was created by a newer version of the app (format ${dto.formatVersion}); update the app to import it"
        }
        if (dto.kind != BACKUP_KIND_FULL && dto.kind != BACKUP_KIND_BOOKMARKS) errors += "unknown backup kind"
        if (errors.isNotEmpty()) return errors
        val d = dto.data
        listOf(
            "bookmarks" to d.bookmarks.size, "snippets" to d.snippets.size, "lessonProgress" to d.lessonProgress.size,
            "quizResults" to d.quizResults.size, "flashcardProgress" to d.flashcardProgress.size,
            "studyDays" to d.studyDays.size, "achievements" to d.achievements.size,
            "searchHistory" to d.searchHistory.size, "recentViews" to d.recentViews.size,
        ).forEach { (name, size) -> if (size > MAX_ROWS) errors += "too many $name entries ($size)" }
        if (errors.isNotEmpty()) return errors

        fun id(value: String, what: String) {
            if (value.isBlank() || value.length > MAX_ID_CHARS) errors += "$what has an invalid id"
        }
        fun text(value: String, max: Int, what: String) {
            if (value.length > max) errors += "$what is too long"
        }
        fun millis(value: Long, what: String) {
            if (value !in 0..MAX_MILLIS) errors += "$what has an invalid date"
        }

        d.bookmarks.forEach {
            if (it.type !in ENTITY_TYPES) errors += "bookmark has unknown type"
            id(it.targetId, "bookmark"); text(it.title, MAX_TITLE_CHARS, "bookmark title"); text(it.subtitle, MAX_TITLE_CHARS, "bookmark subtitle")
            millis(it.createdAtMillis, "bookmark")
        }
        d.snippets.forEach {
            if (it.title.isBlank()) errors += "snippet without title"
            if (it.code.isEmpty()) errors += "snippet without code"
            if (it.collection.isBlank()) errors += "snippet without collection"
            text(it.title, MAX_TITLE_CHARS, "snippet title"); text(it.description, MAX_CODE_CHARS, "snippet description")
            text(it.collection, MAX_TITLE_CHARS, "snippet collection"); text(it.code, MAX_CODE_CHARS, "snippet code")
            if (it.tags.size > MAX_TAGS || it.tags.any { tag -> tag.length > MAX_TAG_CHARS }) errors += "snippet has invalid tags"
            millis(it.createdAtMillis, "snippet"); millis(it.updatedAtMillis, "snippet")
        }
        d.lessonProgress.forEach {
            id(it.lessonId, "lesson progress")
            if (it.status !in LessonStatus.NOT_STARTED..LessonStatus.COMPLETED) errors += "lesson progress has invalid status"
            if (it.bestQuizScorePercent !in 0..100) errors += "lesson progress has invalid score"
            millis(it.lastOpenedAtMillis, "lesson progress")
            it.completedAtMillis?.let { c -> millis(c, "lesson progress") }
        }
        d.quizResults.forEach {
            id(it.lessonId, "quiz result")
            if (it.total !in 1..1_000 || it.correct !in 0..it.total) errors += "quiz result has invalid score"
            millis(it.takenAtMillis, "quiz result")
        }
        d.flashcardProgress.forEach {
            id(it.cardId, "flashcard progress")
            if (it.box !in 0..10) errors += "flashcard progress has invalid box"
            if (it.reviewCount !in 0..1_000_000) errors += "flashcard progress has invalid count"
            if (it.nextReviewEpochDay !in 0..MAX_EPOCH_DAY) errors += "flashcard progress has invalid review day"
        }
        d.studyDays.forEach {
            if (it.epochDay !in 0..MAX_EPOCH_DAY) errors += "study day is out of range"
            if (it.activityCount !in 0..1_000_000) errors += "study day has invalid count"
        }
        d.achievements.forEach { id(it.id, "achievement"); millis(it.unlockedAtMillis, "achievement") }
        d.searchHistory.forEach {
            if (it.query.isBlank() || it.query.length > 500) errors += "search history entry is invalid"
            if (it.useCount !in 1..1_000_000) errors += "search history has invalid count"
            millis(it.lastUsedMillis, "search history")
        }
        d.recentViews.forEach {
            if (it.type !in ENTITY_TYPES) errors += "recent view has unknown type"
            id(it.targetId, "recent view"); text(it.title, MAX_TITLE_CHARS, "recent view title"); text(it.subtitle, MAX_TITLE_CHARS, "recent view subtitle")
            millis(it.viewedAtMillis, "recent view")
        }
        return errors.distinct()
    }
}
