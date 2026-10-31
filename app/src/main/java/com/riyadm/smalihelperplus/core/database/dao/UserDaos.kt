package com.riyadm.smalihelperplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.riyadm.smalihelperplus.core.database.entity.AchievementEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.QuizResultEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentFileEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.SearchHistoryEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.core.database.entity.StudyDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<BookmarkEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE type = :type AND targetId = :targetId)")
    fun observeIsBookmarked(type: String, targetId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE type = :type AND targetId = :targetId")
    suspend fun delete(type: String, targetId: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

@Dao
interface RecentViewDao {
    @Query("SELECT * FROM recent_views ORDER BY viewedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentViewEntity>>

    @Upsert
    suspend fun upsert(item: RecentViewEntity)

    @Query("DELETE FROM recent_views")
    suspend fun clear()

    /** Keeps the newest [keep] entries. */
    @Query("DELETE FROM recent_views WHERE rowid NOT IN (SELECT rowid FROM recent_views ORDER BY viewedAtMillis DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY lastUsedMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE `query` = :query LIMIT 1")
    suspend fun get(query: String): SearchHistoryEntity?

    @Upsert
    suspend fun upsert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets ORDER BY updatedAtMillis DESC")
    suspend fun getAll(): List<SnippetEntity>

    @Query("SELECT * FROM snippets WHERE builtInId IS NULL ORDER BY updatedAtMillis DESC")
    fun observeUserSnippets(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SnippetEntity?

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<SnippetEntity?>

    @Query("SELECT DISTINCT collection FROM snippets ORDER BY collection")
    fun observeCollections(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SnippetEntity): Long

    @Update
    suspend fun update(item: SnippetEntity)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM snippets WHERE builtInId IS NOT NULL")
    suspend fun deleteBuiltIns()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SnippetEntity>)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM lesson_progress")
    fun observeProgress(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress")
    suspend fun getProgress(): List<LessonProgressEntity>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId LIMIT 1")
    suspend fun getProgress(lessonId: String): LessonProgressEntity?

    @Upsert
    suspend fun upsertProgress(item: LessonProgressEntity)

    @Upsert
    suspend fun upsertAllProgress(items: List<LessonProgressEntity>)

    @Insert
    suspend fun insertQuizResult(item: QuizResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuizResults(items: List<QuizResultEntity>)

    @Query("SELECT * FROM quiz_results ORDER BY takenAtMillis DESC")
    fun observeQuizResults(): Flow<List<QuizResultEntity>>

    @Query("SELECT * FROM quiz_results ORDER BY takenAtMillis DESC")
    suspend fun getQuizResults(): List<QuizResultEntity>

    @Query("SELECT * FROM flashcard_progress")
    fun observeFlashcardProgress(): Flow<List<FlashcardProgressEntity>>

    @Query("SELECT * FROM flashcard_progress")
    suspend fun getFlashcardProgress(): List<FlashcardProgressEntity>

    @Upsert
    suspend fun upsertFlashcardProgress(item: FlashcardProgressEntity)

    @Upsert
    suspend fun upsertAllFlashcardProgress(items: List<FlashcardProgressEntity>)

    @Query("SELECT * FROM study_days ORDER BY epochDay DESC")
    fun observeStudyDays(): Flow<List<StudyDayEntity>>

    @Query("SELECT * FROM study_days ORDER BY epochDay DESC")
    suspend fun getStudyDays(): List<StudyDayEntity>

    @Query("SELECT * FROM study_days WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getStudyDay(epochDay: Long): StudyDayEntity?

    @Upsert
    suspend fun upsertStudyDay(item: StudyDayEntity)

    @Upsert
    suspend fun upsertAllStudyDays(items: List<StudyDayEntity>)

    @Query("SELECT * FROM achievements")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAchievements(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(item: AchievementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievements(items: List<AchievementEntity>)

    @Query("DELETE FROM lesson_progress")
    suspend fun clearProgress()

    @Query("DELETE FROM quiz_results")
    suspend fun clearQuizResults()

    @Query("DELETE FROM flashcard_progress")
    suspend fun clearFlashcardProgress()

    @Query("DELETE FROM study_days")
    suspend fun clearStudyDays()

    @Query("DELETE FROM achievements")
    suspend fun clearAchievements()
}

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY openedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentFileEntity>>

    @Upsert
    suspend fun upsert(item: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM recent_files")
    suspend fun clear()
}
