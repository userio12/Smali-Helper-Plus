package com.riyadm.smalihelperplus.data.learning

import com.riyadm.smalihelperplus.core.database.dao.BookmarkDao
import com.riyadm.smalihelperplus.core.database.dao.LearningDao
import com.riyadm.smalihelperplus.core.database.dao.LessonDao
import com.riyadm.smalihelperplus.core.database.dao.RecentViewDao
import com.riyadm.smalihelperplus.core.database.entity.AchievementEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.QuizResultEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.StudyDayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory DAO fakes mirroring the Room queries used by LearningRepository. */
class FakeLessonDao : LessonDao {
    val lessons = MutableStateFlow<List<LessonEntity>>(emptyList())
    val cards = MutableStateFlow<List<FlashcardEntity>>(emptyList())
    override fun observeAll(): Flow<List<LessonEntity>> = lessons.map { l -> l.sortedBy { it.sortOrder } }
    override suspend fun getAll() = lessons.value.sortedBy { it.sortOrder }
    override fun observeById(id: String) = lessons.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun getById(id: String) = lessons.value.firstOrNull { it.id == id }
    override suspend fun count() = lessons.value.size
    override suspend fun insertAll(items: List<LessonEntity>) = lessons.update { old -> (old.filter { o -> items.none { it.id == o.id } } + items) }
    override suspend fun deleteAll() { lessons.value = emptyList() }
    override fun observeFlashcards(): Flow<List<FlashcardEntity>> = cards.map { c -> c.sortedWith(compareBy({ it.topic }, { it.id })) }
    override suspend fun getFlashcards() = cards.value
    override suspend fun insertFlashcards(items: List<FlashcardEntity>) = cards.update { it + items }
    override suspend fun deleteFlashcards() { cards.value = emptyList() }
}

class FakeLearningDao : LearningDao {
    val progress = MutableStateFlow<Map<String, LessonProgressEntity>>(emptyMap())
    val quiz = MutableStateFlow<List<QuizResultEntity>>(emptyList())
    val cards = MutableStateFlow<Map<String, FlashcardProgressEntity>>(emptyMap())
    val days = MutableStateFlow<Map<Long, StudyDayEntity>>(emptyMap())
    val achievements = MutableStateFlow<Map<String, AchievementEntity>>(emptyMap())
    private var nextQuizId = 1L

    override fun observeProgress() = progress.map { it.values.toList() }
    override suspend fun getProgress() = progress.value.values.toList()
    override suspend fun getProgress(lessonId: String) = progress.value[lessonId]
    override suspend fun upsertProgress(item: LessonProgressEntity) = progress.update { it + (item.lessonId to item) }
    override suspend fun upsertAllProgress(items: List<LessonProgressEntity>) = items.forEach { upsertProgress(it) }
    override suspend fun insertQuizResult(item: QuizResultEntity): Long {
        val id = nextQuizId++
        quiz.update { it + item.copy(id = id) }
        return id
    }
    override suspend fun insertQuizResults(items: List<QuizResultEntity>) = items.forEach { insertQuizResult(it) }
    override fun observeQuizResults() = quiz.map { q -> q.sortedByDescending { it.takenAtMillis } }
    override suspend fun getQuizResults() = quiz.value.sortedByDescending { it.takenAtMillis }
    override fun observeFlashcardProgress() = cards.map { it.values.toList() }
    override suspend fun getFlashcardProgress() = cards.value.values.toList()
    override suspend fun upsertFlashcardProgress(item: FlashcardProgressEntity) = cards.update { it + (item.cardId to item) }
    override suspend fun upsertAllFlashcardProgress(items: List<FlashcardProgressEntity>) = items.forEach { upsertFlashcardProgress(it) }
    override fun observeStudyDays() = days.map { d -> d.values.sortedByDescending { it.epochDay } }
    override suspend fun getStudyDays() = days.value.values.sortedByDescending { it.epochDay }
    override suspend fun getStudyDay(epochDay: Long) = days.value[epochDay]
    override suspend fun upsertStudyDay(item: StudyDayEntity) = days.update { it + (item.epochDay to item) }
    override suspend fun upsertAllStudyDays(items: List<StudyDayEntity>) = items.forEach { upsertStudyDay(it) }
    override fun observeAchievements() = achievements.map { it.values.toList() }
    override suspend fun getAchievements() = achievements.value.values.toList()
    override suspend fun insertAchievement(item: AchievementEntity) =
        achievements.update { if (item.id in it) it else it + (item.id to item) }
    override suspend fun insertAchievements(items: List<AchievementEntity>) = items.forEach { insertAchievement(it) }
    override suspend fun clearProgress() { progress.value = emptyMap() }
    override suspend fun clearQuizResults() { quiz.value = emptyList() }
    override suspend fun clearFlashcardProgress() { cards.value = emptyMap() }
    override suspend fun clearStudyDays() { days.value = emptyMap() }
    override suspend fun clearAchievements() { achievements.value = emptyMap() }
}

class FakeBookmarkDao : BookmarkDao {
    val items = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    override fun observeAll() = items
    override suspend fun getAll() = items.value
    override fun observeIsBookmarked(type: String, targetId: String) =
        items.map { list -> list.any { it.type == type && it.targetId == targetId } }
    override suspend fun insert(item: BookmarkEntity): Long {
        if (items.value.any { it.type == item.type && it.targetId == item.targetId }) return -1
        items.update { it + item.copy(id = it.size + 1L) }
        return items.value.size.toLong()
    }
    override suspend fun insertAll(items: List<BookmarkEntity>) = items.forEach { insert(it) }
    override suspend fun delete(type: String, targetId: String) =
        items.update { list -> list.filterNot { it.type == type && it.targetId == targetId } }
    override suspend fun deleteAll() { items.value = emptyList() }
}

class FakeRecentViewDao : RecentViewDao {
    val items = MutableStateFlow<List<RecentViewEntity>>(emptyList())
    override fun observeRecent(limit: Int) = items.map { l -> l.sortedByDescending { it.viewedAtMillis }.take(limit) }
    override suspend fun upsert(item: RecentViewEntity) =
        items.update { list -> list.filterNot { it.type == item.type && it.targetId == item.targetId } + item }
    override suspend fun clear() { items.value = emptyList() }
    override suspend fun trim(keep: Int) = items.update { l -> l.sortedByDescending { it.viewedAtMillis }.take(keep) }
}
