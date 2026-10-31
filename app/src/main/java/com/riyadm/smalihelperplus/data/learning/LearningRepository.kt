package com.riyadm.smalihelperplus.data.learning

import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.content.LessonDto
import com.riyadm.smalihelperplus.core.database.dao.BookmarkDao
import com.riyadm.smalihelperplus.core.database.dao.LearningDao
import com.riyadm.smalihelperplus.core.database.dao.LessonDao
import com.riyadm.smalihelperplus.core.database.dao.RecentViewDao
import com.riyadm.smalihelperplus.core.database.entity.AchievementEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.FlashcardEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonStatus
import com.riyadm.smalihelperplus.core.database.entity.QuizResultEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.StudyDayEntity
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.domain.learning.Achievement
import com.riyadm.smalihelperplus.domain.learning.AchievementEvaluator
import com.riyadm.smalihelperplus.domain.learning.AchievementInputs
import com.riyadm.smalihelperplus.domain.learning.AchievementState
import com.riyadm.smalihelperplus.domain.learning.FlashcardItem
import com.riyadm.smalihelperplus.domain.learning.LearningProgressStats
import com.riyadm.smalihelperplus.domain.learning.LearningSummary
import com.riyadm.smalihelperplus.domain.learning.LeitnerScheduler
import com.riyadm.smalihelperplus.domain.learning.LeitnerState
import com.riyadm.smalihelperplus.domain.learning.LessonLevel
import com.riyadm.smalihelperplus.domain.learning.LessonProgressStatus
import com.riyadm.smalihelperplus.domain.learning.LessonSummary
import com.riyadm.smalihelperplus.domain.learning.LevelProgress
import com.riyadm.smalihelperplus.domain.learning.PracticeSelector
import com.riyadm.smalihelperplus.domain.learning.QuizHistoryEntry
import com.riyadm.smalihelperplus.domain.learning.QuizItem
import com.riyadm.smalihelperplus.domain.learning.QuizOutcome
import com.riyadm.smalihelperplus.domain.learning.ReviewRating
import com.riyadm.smalihelperplus.domain.learning.StreakCalculator
import com.riyadm.smalihelperplus.domain.learning.scorePercent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Source of the user's daily goal; abstracted so the repository can be unit-tested without DataStore. */
fun interface DailyGoalSource {
    fun dailyGoal(): Flow<Int>
}

class PreferencesDailyGoalSource @Inject constructor(private val prefs: UserPreferencesRepository) : DailyGoalSource {
    override fun dailyGoal(): Flow<Int> = prefs.preferences.map { it.dailyGoal }.distinctUntilChanged()
}

/**
 * Single entry point for the Interactive Learning Academy: lesson content joined with progress, quizzes and practice
 * sessions, Leitner flashcards, study-day streaks and achievements. Documentation tables (lessons/flashcards) are
 * read-only here; only user progress tables are written.
 */
@Singleton
class LearningRepository @Inject constructor(
    private val lessonDao: LessonDao,
    private val learningDao: LearningDao,
    private val bookmarkDao: BookmarkDao,
    private val recentViewDao: RecentViewDao,
    private val json: Json,
    private val dailyGoalSource: DailyGoalSource,
    private val clock: LearningClock,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) {
    /** Serialises read-modify-write updates of progress rows. */
    private val writeMutex = Mutex()

    @Volatile private var decodedCache: Pair<List<LessonEntity>, List<LessonDto>>? = null

    // ---------------------------------------------------------------- Lessons

    val lessons: Flow<List<LessonSummary>> =
        combine(lessonDao.observeAll(), learningDao.observeProgress(), learningDao.observeQuizResults()) { lessons, progress, results ->
            val byId = progress.associateBy { it.lessonId }
            val takenQuiz = results.mapTo(HashSet()) { it.lessonId }
            lessons.mapNotNull { it.toSummary(byId[it.lessonIdKey()], it.id in takenQuiz) }
        }.flowOn(default)

    fun lessonSummary(id: String): Flow<LessonSummary?> = lessons.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    fun lesson(id: String): Flow<LessonDto?> =
        lessonDao.observeById(id).map { it?.let(::decode) }.distinctUntilChanged().flowOn(default)

    suspend fun getLesson(id: String): LessonDto? = lessonDao.getById(id)?.let(::decode)

    /** Lesson that follows [id] in curriculum order, if any. */
    suspend fun nextLessonId(id: String): String? {
        val all = lessonDao.getAll()
        val index = all.indexOfFirst { it.id == id }
        return if (index >= 0) all.getOrNull(index + 1)?.id else null
    }

    /** Records that the lesson was opened: moves it to in-progress (never downgrades) and adds a recent view. */
    suspend fun markOpened(lessonId: String) {
        val lesson = lessonDao.getById(lessonId) ?: return
        val now = clock.nowMillis()
        writeMutex.withLock {
            val existing = learningDao.getProgress(lessonId)
            learningDao.upsertProgress(
                existing?.copy(lastOpenedAtMillis = now, status = maxOf(existing.status, LessonStatus.IN_PROGRESS))
                    ?: LessonProgressEntity(lessonId, LessonStatus.IN_PROGRESS, now),
            )
        }
        recentViewDao.upsert(
            RecentViewEntity(
                type = RECENT_TYPE_LESSON,
                targetId = lessonId,
                title = lesson.title,
                subtitle = LessonLevel.fromId(lesson.level)?.title?.let { "$it lesson" } ?: "Lesson",
                viewedAtMillis = now,
            ),
        )
        recentViewDao.trim(RECENT_VIEWS_KEPT)
    }

    /** Marks the lesson completed. Counts as study activity the first time. Returns newly unlocked achievements. */
    suspend fun markCompleted(lessonId: String): List<Achievement> {
        if (lessonDao.getById(lessonId) == null) return emptyList()
        val now = clock.nowMillis()
        val firstCompletion = writeMutex.withLock {
            val existing = learningDao.getProgress(lessonId)
            if (existing?.status == LessonStatus.COMPLETED) {
                false
            } else {
                learningDao.upsertProgress(
                    (existing ?: LessonProgressEntity(lessonId, LessonStatus.IN_PROGRESS, now))
                        .copy(status = LessonStatus.COMPLETED, completedAtMillis = now, lastOpenedAtMillis = now),
                )
                true
            }
        }
        if (firstCompletion) recordStudyActivity()
        return evaluateAchievements()
    }

    /** Moves a completed lesson back to in-progress (e.g. the learner wants to redo it). */
    suspend fun markInProgress(lessonId: String) {
        writeMutex.withLock {
            val existing = learningDao.getProgress(lessonId) ?: return
            learningDao.upsertProgress(existing.copy(status = LessonStatus.IN_PROGRESS, completedAtMillis = null))
        }
    }

    // ---------------------------------------------------------------- Bookmarks

    fun isBookmarked(lessonId: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(BookmarkType.LESSON, lessonId)

    /** Toggles the lesson bookmark; returns true when the lesson is now bookmarked. */
    suspend fun toggleBookmark(lessonId: String): Boolean {
        if (bookmarkDao.observeIsBookmarked(BookmarkType.LESSON, lessonId).first()) {
            bookmarkDao.delete(BookmarkType.LESSON, lessonId)
            return false
        }
        val lesson = lessonDao.getById(lessonId) ?: return false
        bookmarkDao.insert(
            BookmarkEntity(
                type = BookmarkType.LESSON,
                targetId = lessonId,
                title = lesson.title,
                subtitle = LessonLevel.fromId(lesson.level)?.title?.let { "$it lesson" } ?: "Lesson",
                createdAtMillis = clock.nowMillis(),
            ),
        )
        return true
    }

    // ---------------------------------------------------------------- Quizzes

    suspend fun quizForLesson(lessonId: String): List<QuizItem> {
        val lesson = getLesson(lessonId) ?: return emptyList()
        return lesson.quiz.map { QuizItem(lesson.id, lesson.title, it) }
    }

    /** Today's deterministic practice set (same questions all day). */
    suspend fun dailyPractice(): List<QuizItem> = withContext(default) {
        val lessons = decodedLessons()
        val started = learningDao.getProgress().filter { it.status >= LessonStatus.IN_PROGRESS }.mapTo(HashSet()) { it.lessonId }
        PracticeSelector.daily(
            questions = lessons.flatMap { l -> l.quiz.map { QuizItem(l.id, l.title, it) } },
            startedLessonIds = started,
            lessonOrder = lessons.associate { it.id to it.order },
            epochDay = clock.todayEpochDay(),
        )
    }

    /** A random set of questions from all lessons. */
    suspend fun mixedQuiz(random: Random = Random.Default): List<QuizItem> = withContext(default) {
        val lessons = decodedLessons()
        PracticeSelector.mixed(lessons.flatMap { l -> l.quiz.map { QuizItem(l.id, l.title, it) } }, random)
    }

    /**
     * Persists a finished quiz. [lessonId] is a lesson id or one of [PRACTICE_DAILY] / [PRACTICE_MIXED]. For lesson
     * quizzes the best score is kept in the lesson progress.
     */
    suspend fun recordQuizResult(lessonId: String, correct: Int, total: Int): QuizOutcome {
        require(total > 0 && correct in 0..total) { "Invalid quiz result" }
        val now = clock.nowMillis()
        learningDao.insertQuizResult(QuizResultEntity(lessonId = lessonId, correct = correct, total = total, takenAtMillis = now))
        val percent = scorePercent(correct, total)
        var newBest = false
        if (lessonId != PRACTICE_DAILY && lessonId != PRACTICE_MIXED && lessonDao.getById(lessonId) != null) {
            writeMutex.withLock {
                val existing = learningDao.getProgress(lessonId)
                val previousBest = existing?.bestQuizScorePercent ?: 0
                val hadResult = existing != null && learningDao.getQuizResults().count { it.lessonId == lessonId } > 1
                newBest = !hadResult || percent > previousBest
                learningDao.upsertProgress(
                    (existing ?: LessonProgressEntity(lessonId, LessonStatus.IN_PROGRESS, now))
                        .copy(bestQuizScorePercent = maxOf(previousBest, percent)),
                )
            }
        }
        recordStudyActivity()
        return QuizOutcome(correct, total, newBest, evaluateAchievements())
    }

    val quizHistory: Flow<List<QuizHistoryEntry>> =
        combine(learningDao.observeQuizResults(), lessonDao.observeAll()) { results, lessons ->
            val titles = lessons.associate { it.id to it.title }
            results.map {
                QuizHistoryEntry(it.id, it.lessonId, quizTitle(it.lessonId, titles), it.correct, it.total, it.takenAtMillis)
            }
        }.flowOn(default)

    // ---------------------------------------------------------------- Flashcards

    val flashcards: Flow<List<FlashcardItem>> =
        combine(lessonDao.observeFlashcards(), learningDao.observeFlashcardProgress(), clock.today) { cards, progress, today ->
            val byId = progress.associateBy { it.cardId }
            cards.map { it.toItem(byId[it.id], today) }
        }.flowOn(default)

    val dueFlashcardCount: Flow<Int> = flashcards.map { list -> list.count { it.isDue } }.distinctUntilChanged()

    /** Applies a Leitner review and counts it as study activity. Returns newly unlocked achievements. */
    suspend fun reviewFlashcard(cardId: String, rating: ReviewRating): List<Achievement> {
        val today = clock.todayEpochDay()
        writeMutex.withLock {
            val existing = learningDao.getFlashcardProgress().firstOrNull { it.cardId == cardId }
            val state = existing?.let { LeitnerState(it.box, it.nextReviewEpochDay, it.reviewCount) }
            val next = LeitnerScheduler.review(state, rating, today)
            learningDao.upsertFlashcardProgress(FlashcardProgressEntity(cardId, next.box, next.nextReviewEpochDay, next.reviewCount))
        }
        recordStudyActivity()
        return evaluateAchievements()
    }

    // ---------------------------------------------------------------- Activity, streaks, achievements

    /** Adds one activity point to today's study day. */
    suspend fun recordStudyActivity() {
        val today = clock.todayEpochDay()
        writeMutex.withLock {
            val existing = learningDao.getStudyDay(today)
            learningDao.upsertStudyDay(StudyDayEntity(today, (existing?.activityCount ?: 0) + 1))
        }
    }

    val studyDays: Flow<Map<Long, Int>> =
        learningDao.observeStudyDays().map { days -> days.associate { it.epochDay to it.activityCount } }

    val achievements: Flow<List<AchievementState>> = learningDao.observeAchievements().map { rows ->
        val unlocked = rows.associate { it.id to it.unlockedAtMillis }
        Achievement.entries.map { AchievementState(it, unlocked[it.id]) }
    }

    /** Re-evaluates achievement rules and persists newly earned ones. Returns those newly unlocked. */
    suspend fun evaluateAchievements(): List<Achievement> = writeMutex.withLock {
        val lessons = lessonDao.getAll()
        val progress = learningDao.getProgress()
        val results = learningDao.getQuizResults()
        val days = learningDao.getStudyDays()
        val goal = dailyGoalSource.dailyGoal().first()
        val levelOf = lessons.associate { it.id to it.level }
        val inputs = AchievementInputs(
            completedByLevel = progress.filter { it.status == LessonStatus.COMPLETED }
                .mapNotNull { levelOf[it.lessonId] }.groupingBy { it }.eachCount(),
            totalByLevel = lessons.groupingBy { it.level }.eachCount(),
            quizzesTaken = results.size,
            hasPerfectQuiz = results.any { it.total > 0 && it.correct == it.total },
            longestStreak = StreakCalculator.longestStreak(days.map { it.epochDay }),
            flashcardReviews = learningDao.getFlashcardProgress().sumOf { it.reviewCount },
            bestDayActivity = days.maxOfOrNull { it.activityCount } ?: 0,
            dailyGoal = goal,
        )
        val unlocked = learningDao.getAchievements().mapTo(HashSet()) { it.id }
        val fresh = AchievementEvaluator.newlyEarned(inputs, unlocked)
        if (fresh.isNotEmpty()) {
            val now = clock.nowMillis()
            learningDao.insertAchievements(fresh.map { AchievementEntity(it.id, now) })
        }
        fresh
    }

    // ---------------------------------------------------------------- Summaries

    /** Overview for the Learn header and the Home screen. */
    val summary: Flow<LearningSummary> = combine(
        lessons,
        combine(learningDao.observeStudyDays(), learningDao.observeQuizResults(), clock.today, ::Triple),
        dailyGoalSource.dailyGoal(),
        dueFlashcardCount,
    ) { lessons, (days, results, today), goal, due ->
        val activeDays = days.map { it.epochDay }
        LearningSummary(
            completedLessons = lessons.count { it.status == LessonProgressStatus.COMPLETED },
            totalLessons = lessons.size,
            continueLesson = continueLesson(lessons),
            currentStreak = StreakCalculator.currentStreak(activeDays, today),
            longestStreak = StreakCalculator.longestStreak(activeDays),
            todayActivity = days.firstOrNull { it.epochDay == today }?.activityCount ?: 0,
            dailyGoal = goal,
            averageQuizScore = averageScore(results),
            quizzesTaken = results.size,
            dueFlashcards = due,
            dailyPracticeDoneToday = results.any { it.lessonId == PRACTICE_DAILY && clock.epochDayOf(it.takenAtMillis) == today },
        )
    }.flowOn(default)

    val progressStats: Flow<LearningProgressStats> = combine(
        lessons,
        combine(learningDao.observeStudyDays(), learningDao.observeFlashcardProgress(), clock.today, ::Triple),
        achievements,
        quizHistory,
    ) { lessons, (days, cards, today), achievements, history ->
        val activeDays = days.map { it.epochDay }
        LearningProgressStats(
            levels = LessonLevel.entries.map { level ->
                val inLevel = lessons.filter { it.level == level }
                LevelProgress(level, inLevel.count { it.status == LessonProgressStatus.COMPLETED }, inLevel.size)
            },
            averageQuizScore = if (history.isEmpty()) null else history.sumOf { it.percent } / history.size,
            quizzesTaken = history.size,
            flashcardReviews = cards.sumOf { it.reviewCount },
            currentStreak = StreakCalculator.currentStreak(activeDays, today),
            longestStreak = StreakCalculator.longestStreak(activeDays),
            todayEpochDay = today,
            studyDays = days.associate { it.epochDay to it.activityCount },
            achievements = achievements,
            history = history,
        )
    }.flowOn(default)

    // ---------------------------------------------------------------- Helpers

    private fun decode(entity: LessonEntity): LessonDto? =
        runCatching { json.decodeFromString<LessonDto>(entity.contentJson) }.getOrNull()

    private suspend fun decodedLessons(): List<LessonDto> {
        val entities = lessonDao.getAll()
        decodedCache?.let { (cachedEntities, decoded) -> if (cachedEntities == entities) return decoded }
        val decoded = entities.mapNotNull(::decode)
        decodedCache = entities to decoded
        return decoded
    }

    private fun LessonEntity.lessonIdKey() = id

    private fun LessonEntity.toSummary(progress: LessonProgressEntity?, quizTaken: Boolean): LessonSummary? {
        val level = LessonLevel.fromId(level) ?: return null
        val status = when (progress?.status) {
            LessonStatus.COMPLETED -> LessonProgressStatus.COMPLETED
            LessonStatus.IN_PROGRESS -> LessonProgressStatus.IN_PROGRESS
            else -> LessonProgressStatus.NOT_STARTED
        }
        return LessonSummary(
            id = id,
            level = level,
            order = sortOrder,
            title = title,
            summary = summary,
            estimatedMinutes = estimatedMinutes,
            status = status,
            bestQuizScore = if (quizTaken) progress?.bestQuizScorePercent else null,
            lastOpenedAtMillis = progress?.lastOpenedAtMillis,
            completedAtMillis = progress?.completedAtMillis,
        )
    }

    private fun FlashcardEntity.toItem(progress: FlashcardProgressEntity?, today: Long): FlashcardItem {
        val state = progress?.let { LeitnerState(it.box, it.nextReviewEpochDay, it.reviewCount) }
        return FlashcardItem(
            id = id, topic = topic, front = front, back = back, code = code,
            box = state?.box ?: 0, reviewCount = state?.reviewCount ?: 0,
            isDue = LeitnerScheduler.isDue(state, today),
        )
    }

    companion object {
        const val PRACTICE_DAILY = "daily"
        const val PRACTICE_MIXED = "mixed"
        const val RECENT_TYPE_LESSON = "lesson"
        private const val RECENT_VIEWS_KEPT = 200

        fun quizTitle(lessonId: String, titles: Map<String, String>): String = when (lessonId) {
            PRACTICE_DAILY -> "Daily practice"
            PRACTICE_MIXED -> "Mixed quiz"
            else -> titles[lessonId] ?: "Lesson quiz"
        }

        fun averageScore(results: List<QuizResultEntity>): Int? =
            if (results.isEmpty()) null else results.sumOf { scorePercent(it.correct, it.total) } / results.size

        /** Most recently opened unfinished lesson; otherwise the first lesson that is not completed. */
        fun continueLesson(lessons: List<LessonSummary>): LessonSummary? =
            lessons.filter { it.status == LessonProgressStatus.IN_PROGRESS }.maxByOrNull { it.lastOpenedAtMillis ?: 0L }
                ?: lessons.sortedBy { it.order }.firstOrNull { it.status != LessonProgressStatus.COMPLETED }
    }
}
