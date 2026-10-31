package com.riyadm.smalihelperplus.domain.learning

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.content.QuizQuestionDto
import org.junit.Test
import kotlin.random.Random

class PracticeSelectorTest {
    private val lessons = (1..10).map { "lesson-$it" }
    private val order = lessons.withIndex().associate { (i, id) -> id to i + 1 }
    private val questions = lessons.flatMap { id ->
        (1..5).map { q -> QuizItem(id, id, QuizQuestionDto("$id-q$q", "Q$q of $id", null, listOf("a", "b"), 0, "because")) }
    }

    @Test
    fun dailyIsDeterministicForTheSameDay() {
        val a = PracticeSelector.daily(questions, setOf("lesson-2", "lesson-5"), order, epochDay = 20_400)
        val b = PracticeSelector.daily(questions.shuffled(Random(1)), setOf("lesson-5", "lesson-2"), order, epochDay = 20_400)
        assertThat(a.map { it.question.id }).isEqualTo(b.map { it.question.id })
        assertThat(a).hasSize(PracticeSelector.DAILY_COUNT)
    }

    @Test
    fun dailyChangesBetweenDays() {
        val started = lessons.toSet()
        val days = (20_000L until 20_010L).map { day -> PracticeSelector.daily(questions, started, order, day).map { it.question.id } }
        assertThat(days.toSet().size).isGreaterThan(1)
    }

    @Test
    fun dailyPrioritisesStartedLessons() {
        val started = setOf("lesson-3", "lesson-7")
        val picked = PracticeSelector.daily(questions, started, order, epochDay = 19_000)
        // 2 started lessons x 5 questions = 10 >= 8, so everything comes from them.
        assertThat(picked.map { it.lessonId }.toSet()).isEqualTo(started)
        assertThat(picked.map { it.question.id }.toSet()).hasSize(8)
    }

    @Test
    fun dailyFillsFromEarliestUnstartedLessons() {
        val picked = PracticeSelector.daily(questions, setOf("lesson-9"), order, epochDay = 19_001)
        val fromStarted = picked.count { it.lessonId == "lesson-9" }
        assertThat(fromStarted).isEqualTo(5)
        // The remaining 3 come round-robin from lessons 1, 2 and 3 (curriculum order).
        assertThat(picked.filter { it.lessonId != "lesson-9" }.map { it.lessonId }.toSet())
            .isEqualTo(setOf("lesson-1", "lesson-2", "lesson-3"))
    }

    @Test
    fun dailySpreadsAcrossLessonsRoundRobin() {
        val picked = PracticeSelector.daily(questions, lessons.toSet(), order, epochDay = 18_500)
        assertThat(picked.groupBy { it.lessonId }.values.maxOf { it.size }).isEqualTo(1)
    }

    @Test
    fun dailyHandlesSmallOrEmptyPools() {
        assertThat(PracticeSelector.daily(emptyList(), emptySet(), emptyMap(), 1)).isEmpty()
        val few = questions.take(3)
        assertThat(PracticeSelector.daily(few, emptySet(), order, 1)).hasSize(3)
    }

    @Test
    fun mixedDrawsTenDistinctQuestions() {
        val picked = PracticeSelector.mixed(questions, Random(42))
        assertThat(picked).hasSize(PracticeSelector.MIXED_COUNT)
        assertThat(picked.map { it.question.id }.toSet()).hasSize(PracticeSelector.MIXED_COUNT)
        assertThat(picked.map { it.lessonId }.toSet()).hasSize(10)
    }
}
