package com.riyadm.smalihelperplus.data.learning

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.content.LessonDto
import com.riyadm.smalihelperplus.core.content.LessonSectionDto
import com.riyadm.smalihelperplus.core.content.QuizQuestionDto
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.FlashcardEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonStatus
import com.riyadm.smalihelperplus.domain.learning.Achievement
import com.riyadm.smalihelperplus.domain.learning.LessonProgressStatus
import com.riyadm.smalihelperplus.domain.learning.ReviewRating
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class LearningRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val today = LocalDate.of(2026, 10, 30)

    private class FixedClock(var millis: Long) : LearningClock() {
        override fun nowMillis() = millis
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private val clock = FixedClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
    private val lessonDao = FakeLessonDao()
    private val learningDao = FakeLearningDao()
    private val bookmarkDao = FakeBookmarkDao()
    private val recentDao = FakeRecentViewDao()
    private val goal = MutableStateFlow(3)

    private fun lesson(id: String, level: String, order: Int, questions: Int = 4) = LessonDto(
        id = id, level = level, order = order, title = "Title $id", summary = "Summary", estimatedMinutes = 5,
        sections = listOf(LessonSectionDto("text", null, "Body")),
        quiz = (1..questions).map { QuizQuestionDto("$id-q$it", "Question $it", null, listOf("right", "wrong"), 0, "Because.") },
    )

    private fun repo(dispatcher: CoroutineDispatcher): LearningRepository {
        val dtos = listOf(lesson("a", "beginner", 1), lesson("b", "beginner", 2), lesson("c", "intermediate", 11))
        lessonDao.lessons.value = dtos.map {
            LessonEntity(it.id, it.level, it.order, it.title, it.summary, it.estimatedMinutes, json.encodeToString(LessonDto.serializer(), it))
        }
        lessonDao.cards.value = (1..3).map { FlashcardEntity("card-$it", "Opcodes", "front $it", "back $it", null) }
        return LearningRepository(lessonDao, learningDao, bookmarkDao, recentDao, json, { goal }, clock, dispatcher)
    }

    @Test
    fun lessonsStartNotStartedAndOpeningMarksInProgress() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertThat(repo.lessons.first().map { it.status }.toSet()).containsExactly(LessonProgressStatus.NOT_STARTED)
        assertThat(repo.summary.first().continueLesson?.id).isEqualTo("a")

        repo.markOpened("b")
        val b = repo.lessons.first().first { it.id == "b" }
        assertThat(b.status).isEqualTo(LessonProgressStatus.IN_PROGRESS)
        assertThat(repo.summary.first().continueLesson?.id).isEqualTo("b")
        assertThat(recentDao.items.value.single().targetId).isEqualTo("b")
        assertThat(recentDao.items.value.single().type).isEqualTo("lesson")
    }

    @Test
    fun completingCountsActivityOnceAndUnlocksFirstLesson() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        val unlocked = repo.markCompleted("a")
        assertThat(unlocked).contains(Achievement.FIRST_LESSON)
        assertThat(repo.markCompleted("a")).isEmpty()
        assertThat(learningDao.days.value[today.toEpochDay()]?.activityCount).isEqualTo(1)
        // Opening a completed lesson does not downgrade it.
        repo.markOpened("a")
        assertThat(learningDao.progress.value["a"]?.status).isEqualTo(LessonStatus.COMPLETED)

        val summary = repo.summary.first()
        assertThat(summary.completedLessons).isEqualTo(1)
        assertThat(summary.currentStreak).isEqualTo(1)
        assertThat(summary.todayActivity).isEqualTo(1)
    }

    @Test
    fun completingAllBeginnerLessonsUnlocksLevelAchievement() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        repo.markCompleted("a")
        assertThat(repo.markCompleted("b")).contains(Achievement.BEGINNER_COMPLETE)
        assertThat(learningDao.achievements.value.keys).containsAtLeast("first_lesson", "beginner_complete")
    }

    @Test
    fun quizResultsKeepBestScore() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        val first = repo.recordQuizResult("a", correct = 2, total = 4)
        assertThat(first.percent).isEqualTo(50)
        assertThat(first.isNewBest).isTrue()
        assertThat(first.newAchievements).contains(Achievement.FIRST_QUIZ)

        val worse = repo.recordQuizResult("a", correct = 1, total = 4)
        assertThat(worse.isNewBest).isFalse()
        assertThat(learningDao.progress.value["a"]?.bestQuizScorePercent).isEqualTo(50)

        val perfect = repo.recordQuizResult("a", correct = 4, total = 4)
        assertThat(perfect.isNewBest).isTrue()
        assertThat(perfect.newAchievements).contains(Achievement.PERFECT_QUIZ)
        assertThat(repo.lessons.first().first { it.id == "a" }.bestQuizScore).isEqualTo(100)

        val summary = repo.summary.first()
        assertThat(summary.quizzesTaken).isEqualTo(3)
        assertThat(summary.averageQuizScore).isEqualTo((50 + 25 + 100) / 3)
        // Three quiz finishes reach the default goal of 3.
        assertThat(summary.dailyGoalMet).isTrue()
        assertThat(learningDao.achievements.value.keys).contains("daily_goal")
    }

    @Test
    fun practiceResultsDoNotTouchLessonProgress() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        repo.recordQuizResult(LearningRepository.PRACTICE_DAILY, 3, 8)
        assertThat(learningDao.progress.value).isEmpty()
        assertThat(repo.summary.first().dailyPracticeDoneToday).isTrue()
        assertThat(repo.quizHistory.first().single().title).isEqualTo("Daily practice")
    }

    @Test
    fun dailyPracticeIsStableWithinADay() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        repo.markOpened("c")
        val first = repo.dailyPractice().map { it.question.id }
        val second = repo.dailyPractice().map { it.question.id }
        assertThat(first).isEqualTo(second)
        assertThat(first).hasSize(8)
        // Started lesson "c" contributes all of its 4 questions.
        assertThat(first.count { it.startsWith("c-") }).isEqualTo(4)
    }

    @Test
    fun flashcardReviewSchedulesAndCountsActivity() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertThat(repo.dueFlashcardCount.first()).isEqualTo(3)
        repo.reviewFlashcard("card-1", ReviewRating.GOOD)
        repo.reviewFlashcard("card-2", ReviewRating.AGAIN)
        assertThat(repo.dueFlashcardCount.first()).isEqualTo(2)
        val card1 = learningDao.cards.value.getValue("card-1")
        assertThat(card1.box).isEqualTo(2)
        assertThat(card1.nextReviewEpochDay).isEqualTo(today.toEpochDay() + 1)
        assertThat(learningDao.days.value[today.toEpochDay()]?.activityCount).isEqualTo(2)

        // The next day card-1 is due again.
        clock.millis += 24 * 60 * 60 * 1000L
        assertThat(repo.dueFlashcardCount.first()).isEqualTo(3)
    }

    @Test
    fun bookmarkToggles() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertThat(repo.toggleBookmark("a")).isTrue()
        assertThat(bookmarkDao.items.value.single().type).isEqualTo(BookmarkType.LESSON)
        assertThat(repo.isBookmarked("a").first()).isTrue()
        assertThat(repo.toggleBookmark("a")).isFalse()
        assertThat(repo.isBookmarked("a").first()).isFalse()
    }

    @Test
    fun streakAchievementFromConsecutiveDays() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        repeat(3) { day ->
            clock.millis = today.plusDays(day.toLong()).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            repo.recordStudyActivity()
        }
        assertThat(repo.evaluateAchievements()).contains(Achievement.STREAK_3)
        val stats = repo.progressStats.first()
        assertThat(stats.currentStreak).isEqualTo(3)
        assertThat(stats.longestStreak).isEqualTo(3)
        assertThat(stats.achievements.first { it.achievement == Achievement.STREAK_3 }.unlocked).isTrue()
    }

    @Test
    fun unknownLessonIsIgnored() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        repo.markOpened("missing")
        assertThat(repo.markCompleted("missing")).isEmpty()
        assertThat(learningDao.progress.value).isEmpty()
        assertThat(repo.quizForLesson("missing")).isEmpty()
        assertThat(repo.nextLessonId("a")).isEqualTo("b")
        assertThat(repo.nextLessonId("c")).isNull()
    }
}
