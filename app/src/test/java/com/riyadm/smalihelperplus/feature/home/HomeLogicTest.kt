package com.riyadm.smalihelperplus.feature.home

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonStatus
import com.riyadm.smalihelperplus.core.database.entity.QuizResultEntity
import org.junit.Test

class HomeLogicTest {
    private fun lesson(id: String, order: Int) = LessonEntity(id, "beginner", order, "Title $id", "Summary $id", 10, "{}")
    private fun progress(id: String, status: Int, opened: Long = 0) = LessonProgressEntity(id, status, opened)
    private val lessons = listOf(lesson("c", 3), lesson("a", 1), lesson("b", 2))

    @Test
    fun greetingByHour() {
        assertThat(HomeLogic.greetingFor(5)).isEqualTo("Good morning")
        assertThat(HomeLogic.greetingFor(11)).isEqualTo("Good morning")
        assertThat(HomeLogic.greetingFor(12)).isEqualTo("Good afternoon")
        assertThat(HomeLogic.greetingFor(17)).isEqualTo("Good afternoon")
        assertThat(HomeLogic.greetingFor(18)).isEqualTo("Good evening")
        assertThat(HomeLogic.greetingFor(22)).isEqualTo("Good evening")
        assertThat(HomeLogic.greetingFor(23)).isEqualTo("Good night")
        assertThat(HomeLogic.greetingFor(0)).isEqualTo("Good night")
        assertThat(HomeLogic.greetingFor(4)).isEqualTo("Good night")
    }

    @Test
    fun noLessons() {
        assertThat(HomeLogic.continueLearning(emptyList(), emptyList())).isEqualTo(ContinueLearning.None)
    }

    @Test
    fun firstNotStartedLessonByOrder() {
        val result = HomeLogic.continueLearning(lessons, emptyList()) as ContinueLearning.Next
        assertThat(result.lesson.id).isEqualTo("a")
        assertThat(result.inProgress).isFalse()

        val afterFirst = HomeLogic.continueLearning(lessons, listOf(progress("a", LessonStatus.COMPLETED))) as ContinueLearning.Next
        assertThat(afterFirst.lesson.id).isEqualTo("b")
    }

    @Test
    fun inProgressWinsAndMostRecentIsChosen() {
        val result = HomeLogic.continueLearning(
            lessons,
            listOf(
                progress("a", LessonStatus.COMPLETED),
                progress("b", LessonStatus.IN_PROGRESS, opened = 100),
                progress("c", LessonStatus.IN_PROGRESS, opened = 200),
            ),
        ) as ContinueLearning.Next
        assertThat(result.lesson.id).isEqualTo("c")
        assertThat(result.inProgress).isTrue()
    }

    @Test
    fun allCompleted() {
        val done = lessons.map { progress(it.id, LessonStatus.COMPLETED) }
        assertThat(HomeLogic.continueLearning(lessons, done)).isEqualTo(ContinueLearning.AllDone)
    }

    @Test
    fun statsCountOnlyInstalledLessons() {
        val stats = HomeLogic.stats(
            lessons = lessons,
            progress = listOf(
                progress("a", LessonStatus.COMPLETED),
                progress("b", LessonStatus.IN_PROGRESS),
                progress("removed", LessonStatus.COMPLETED),
            ),
            quizResults = listOf(
                QuizResultEntity(1, "a", 8, 10, 1),
                QuizResultEntity(2, "daily", 5, 10, 2),
                QuizResultEntity(3, "mixed", 0, 0, 3),
            ),
            studyEpochDays = listOf(100L, 99L, 98L, 50L),
            todayEpochDay = 100L,
        )
        assertThat(stats.lessonsCompleted).isEqualTo(1)
        assertThat(stats.lessonsTotal).isEqualTo(3)
        assertThat(stats.streak).isEqualTo(3)
        assertThat(stats.quizzesTaken).isEqualTo(3)
        assertThat(stats.averageScorePercent).isEqualTo(65) // (80 + 50) / 2, the empty quiz is ignored
        assertThat(stats.completedFraction).isWithin(0.001f).of(1f / 3f)
    }

    @Test
    fun statsWithoutData() {
        val stats = HomeLogic.stats(emptyList(), emptyList(), emptyList(), emptyList(), 10)
        assertThat(stats.averageScorePercent).isNull()
        assertThat(stats.streak).isEqualTo(0)
        assertThat(stats.completedFraction).isEqualTo(0f)
    }
}
