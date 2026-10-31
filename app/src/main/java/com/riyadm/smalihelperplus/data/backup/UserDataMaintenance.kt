package com.riyadm.smalihelperplus.data.backup

import androidx.room.withTransaction
import com.riyadm.smalihelperplus.core.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Destructive-but-user-requested clean-ups offered in Settings > Data. Documentation content is never touched. */
@Singleton
class UserDataMaintenance @Inject constructor(private val db: AppDatabase) {
    suspend fun clearSearchHistory() = db.searchHistoryDao().clear()

    suspend fun clearRecentlyViewed() = db.recentViewDao().clear()

    /** Resets lesson progress, quiz results, flashcard progress, study days (streak) and achievements atomically. */
    suspend fun resetLearningProgress() = db.withTransaction {
        db.learningDao().apply {
            clearProgress(); clearQuizResults(); clearFlashcardProgress(); clearStudyDays(); clearAchievements()
        }
    }
}
