package com.riyadm.smalihelperplus.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** Documentation content (replaced atomically by content updates). */
@Entity(tableName = "opcodes", indices = [Index("category"), Index("opcode"), Index("format")])
data class OpcodeEntity(
    @PrimaryKey val name: String,
    val opcode: Int,
    val category: String,
    val format: String,
    val syntax: String,
    /** JSON-encoded List<OperandDto>. */
    val operandsJson: String,
    val summary: String,
    val description: String,
    val technical: String,
    val example: String,
    val exampleExplanation: String,
    val javaEquivalent: String?,
    val kotlinEquivalent: String?,
    val registerUsage: String,
    val minApi: Int,
    val dexVersion: String,
    val compatibility: String,
    val commonMistakes: List<String>,
    val related: List<String>,
    /** JSON-encoded List<ReferenceDto>. */
    val referencesJson: String,
    val tags: List<String>,
)

/** Full-text index over the opcode documentation (external content table = opcodes). */
@Fts4(contentEntity = OpcodeEntity::class)
@Entity(tableName = "opcodes_fts")
data class OpcodeFtsEntity(
    val name: String,
    val category: String,
    val format: String,
    val syntax: String,
    val summary: String,
    val description: String,
    val example: String,
)

/** Lesson documentation content (replaced atomically by content updates). */
@Entity(tableName = "lessons", indices = [Index("level")])
data class LessonEntity(
    @PrimaryKey val id: String,
    val level: String,
    val sortOrder: Int,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int,
    /** Full JSON-encoded LessonDto. */
    val contentJson: String,
)

@Entity(tableName = "flashcards", indices = [Index("topic")])
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val topic: String,
    val front: String,
    val back: String,
    val code: String?,
)

/** Version metadata per content package ("opcodes", "lessons", "snippets"). */
@Entity(tableName = "content_metadata")
data class ContentMetadataEntity(
    @PrimaryKey val packageId: String,
    val contentVersion: Int,
    val schemaVersion: Int,
    /** "bundled" or "remote" */
    val source: String,
    val description: String,
    val updatedAtMillis: Long,
)

// ---------------- User data (never touched by documentation updates) ----------------

object BookmarkType {
    const val OPCODE = "opcode"
    const val LESSON = "lesson"
    const val SNIPPET = "snippet"
}

@Entity(tableName = "bookmarks", indices = [Index(value = ["type", "targetId"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val targetId: String,
    val title: String,
    val subtitle: String = "",
    val createdAtMillis: Long,
)

@Entity(tableName = "recent_views", primaryKeys = ["type", "targetId"], indices = [Index("viewedAtMillis")])
data class RecentViewEntity(
    val type: String,
    val targetId: String,
    val title: String,
    val subtitle: String = "",
    val viewedAtMillis: Long,
)

@Entity(tableName = "search_history", indices = [Index("lastUsedMillis")])
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsedMillis: Long,
    val useCount: Int = 1,
)

@Entity(tableName = "snippets", indices = [Index("collection"), Index("builtInId", unique = true)])
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val collection: String = "My snippets",
    val code: String,
    val tags: List<String> = emptyList(),
    /** Non-null for snippets seeded from bundled content; user snippets have null. */
    val builtInId: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

object LessonStatus {
    const val NOT_STARTED = 0
    const val IN_PROGRESS = 1
    const val COMPLETED = 2
}

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val status: Int,
    val lastOpenedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val bestQuizScorePercent: Int = 0,
)

@Entity(tableName = "quiz_results", indices = [Index("lessonId"), Index("takenAtMillis")])
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Lesson id, or "daily" / "mixed" for practice sessions. */
    val lessonId: String,
    val correct: Int,
    val total: Int,
    val takenAtMillis: Long,
)

/** Leitner-box spaced repetition state for a flashcard. */
@Entity(tableName = "flashcard_progress")
data class FlashcardProgressEntity(
    @PrimaryKey val cardId: String,
    val box: Int,
    val nextReviewEpochDay: Long,
    val reviewCount: Int,
)

/** One row per calendar day with learning activity; used for streaks. */
@Entity(tableName = "study_days")
data class StudyDayEntity(
    @PrimaryKey val epochDay: Long,
    val activityCount: Int,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAtMillis: Long,
)

@Entity(tableName = "recent_files", indices = [Index("openedAtMillis")])
data class RecentFileEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val openedAtMillis: Long,
)
