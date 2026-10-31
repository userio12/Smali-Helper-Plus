package com.riyadm.smalihelperplus.data.backup

import com.riyadm.smalihelperplus.core.database.entity.AchievementEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.QuizResultEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.SearchHistoryEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.core.database.entity.StudyDayEntity
import kotlinx.serialization.Serializable

/** Bump only for breaking changes of the backup layout; older versions stay importable. */
const val BACKUP_FORMAT_VERSION = 2
const val BACKUP_KIND_FULL = "full"
const val BACKUP_KIND_BOOKMARKS = "bookmarks"

/** Hard limit for a backup file read from storage. */
const val MAX_BACKUP_BYTES = 20L * 1024 * 1024

@Serializable
data class BackupDto(
    val formatVersion: Int,
    val appVersionName: String = "",
    /** ISO-8601 UTC instant. */
    val exportedAt: String = "",
    /** "full" or "bookmarks". */
    val kind: String = BACKUP_KIND_FULL,
    val data: BackupData = BackupData(),
)

/** All user-owned data. Documentation content (opcodes, lessons, built-in snippets) is never part of a backup. */
@Serializable
data class BackupData(
    val bookmarks: List<BookmarkBackup> = emptyList(),
    val snippets: List<SnippetBackup> = emptyList(),
    val lessonProgress: List<LessonProgressBackup> = emptyList(),
    val quizResults: List<QuizResultBackup> = emptyList(),
    val flashcardProgress: List<FlashcardProgressBackup> = emptyList(),
    val studyDays: List<StudyDayBackup> = emptyList(),
    val achievements: List<AchievementBackup> = emptyList(),
    val searchHistory: List<SearchHistoryBackup> = emptyList(),
    val recentViews: List<RecentViewBackup> = emptyList(),
) {
    fun counts() = BackupCounts(
        bookmarks.size, snippets.size, lessonProgress.size, quizResults.size, flashcardProgress.size,
        studyDays.size, achievements.size, searchHistory.size, recentViews.size,
    )
}

@Serializable
data class BookmarkBackup(
    val type: String,
    val targetId: String,
    val title: String,
    val subtitle: String = "",
    val createdAtMillis: Long,
)

@Serializable
data class SnippetBackup(
    val title: String,
    val description: String = "",
    val collection: String = "My snippets",
    val code: String,
    val tags: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class LessonProgressBackup(
    val lessonId: String,
    val status: Int,
    val lastOpenedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val bestQuizScorePercent: Int = 0,
)

@Serializable
data class QuizResultBackup(val lessonId: String, val correct: Int, val total: Int, val takenAtMillis: Long)

@Serializable
data class FlashcardProgressBackup(val cardId: String, val box: Int, val nextReviewEpochDay: Long, val reviewCount: Int)

@Serializable
data class StudyDayBackup(val epochDay: Long, val activityCount: Int)

@Serializable
data class AchievementBackup(val id: String, val unlockedAtMillis: Long)

@Serializable
data class SearchHistoryBackup(val query: String, val lastUsedMillis: Long, val useCount: Int = 1)

@Serializable
data class RecentViewBackup(
    val type: String,
    val targetId: String,
    val title: String,
    val subtitle: String = "",
    val viewedAtMillis: Long,
)

data class BackupCounts(
    val bookmarks: Int = 0,
    val snippets: Int = 0,
    val lessonProgress: Int = 0,
    val quizResults: Int = 0,
    val flashcardProgress: Int = 0,
    val studyDays: Int = 0,
    val achievements: Int = 0,
    val searchHistory: Int = 0,
    val recentViews: Int = 0,
) {
    val total: Int
        get() = bookmarks + snippets + lessonProgress + quizResults + flashcardProgress + studyDays + achievements + searchHistory + recentViews

    /** Non-zero rows as "label to count" for display. */
    fun lines(): List<Pair<String, Int>> = listOf(
        "Bookmarks" to bookmarks,
        "Your snippets" to snippets,
        "Lesson progress" to lessonProgress,
        "Quiz results" to quizResults,
        "Flashcard progress" to flashcardProgress,
        "Study days" to studyDays,
        "Achievements" to achievements,
        "Search history" to searchHistory,
        "Recently viewed" to recentViews,
    ).filter { it.second > 0 }
}

enum class ImportMode { MERGE, REPLACE }

data class ImportSummary(val mode: ImportMode, val written: BackupCounts, val bookmarksOnly: Boolean)

/** Presentable failure of reading/validating/writing a backup. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

// ---- entity mapping -------------------------------------------------------------------------------------------

fun BookmarkEntity.toBackup() = BookmarkBackup(type, targetId, title, subtitle, createdAtMillis)
fun BookmarkBackup.toEntity() = BookmarkEntity(type = type, targetId = targetId, title = title, subtitle = subtitle, createdAtMillis = createdAtMillis)

fun SnippetEntity.toBackup() = SnippetBackup(title, description, collection, code, tags, createdAtMillis, updatedAtMillis)
fun SnippetBackup.toEntity() = SnippetEntity(
    title = title, description = description, collection = collection, code = code, tags = tags, builtInId = null,
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun LessonProgressEntity.toBackup() = LessonProgressBackup(lessonId, status, lastOpenedAtMillis, completedAtMillis, bestQuizScorePercent)
fun LessonProgressBackup.toEntity() = LessonProgressEntity(lessonId, status, lastOpenedAtMillis, completedAtMillis, bestQuizScorePercent)

fun QuizResultEntity.toBackup() = QuizResultBackup(lessonId, correct, total, takenAtMillis)
fun QuizResultBackup.toEntity() = QuizResultEntity(lessonId = lessonId, correct = correct, total = total, takenAtMillis = takenAtMillis)

fun FlashcardProgressEntity.toBackup() = FlashcardProgressBackup(cardId, box, nextReviewEpochDay, reviewCount)
fun FlashcardProgressBackup.toEntity() = FlashcardProgressEntity(cardId, box, nextReviewEpochDay, reviewCount)

fun StudyDayEntity.toBackup() = StudyDayBackup(epochDay, activityCount)
fun StudyDayBackup.toEntity() = StudyDayEntity(epochDay, activityCount)

fun AchievementEntity.toBackup() = AchievementBackup(id, unlockedAtMillis)
fun AchievementBackup.toEntity() = AchievementEntity(id, unlockedAtMillis)

fun SearchHistoryEntity.toBackup() = SearchHistoryBackup(query, lastUsedMillis, useCount)
fun SearchHistoryBackup.toEntity() = SearchHistoryEntity(query, lastUsedMillis, useCount)

fun RecentViewEntity.toBackup() = RecentViewBackup(type, targetId, title, subtitle, viewedAtMillis)
fun RecentViewBackup.toEntity() = RecentViewEntity(type, targetId, title, subtitle, viewedAtMillis)
