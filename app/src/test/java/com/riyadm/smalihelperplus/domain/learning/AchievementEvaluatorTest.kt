package com.riyadm.smalihelperplus.domain.learning

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AchievementEvaluatorTest {
    private val totals = mapOf("beginner" to 10, "intermediate" to 9, "advanced" to 9)
    private val none = AchievementInputs(
        completedByLevel = emptyMap(), totalByLevel = totals, quizzesTaken = 0, hasPerfectQuiz = false,
        longestStreak = 0, flashcardReviews = 0, bestDayActivity = 0, dailyGoal = 3,
    )

    @Test
    fun nothingEarnedForNewLearner() {
        assertThat(AchievementEvaluator.earned(none)).isEmpty()
    }

    @Test
    fun ids_are_unique_and_about_a_dozen() {
        assertThat(Achievement.entries.map { it.id }.toSet()).hasSize(Achievement.entries.size)
        assertThat(Achievement.entries.size).isAtLeast(12)
        assertThat(Achievement.fromId("streak_7")).isEqualTo(Achievement.STREAK_7)
        assertThat(Achievement.fromId("nope")).isNull()
    }

    @Test
    fun lessonAndLevelAchievements() {
        val one = AchievementEvaluator.earned(none.copy(completedByLevel = mapOf("beginner" to 1)))
        assertThat(one).containsExactly(Achievement.FIRST_LESSON)

        val beginnerDone = AchievementEvaluator.earned(none.copy(completedByLevel = mapOf("beginner" to 10, "advanced" to 8)))
        assertThat(beginnerDone).containsAtLeast(Achievement.FIRST_LESSON, Achievement.BEGINNER_COMPLETE)
        assertThat(beginnerDone).containsNoneOf(Achievement.ADVANCED_COMPLETE, Achievement.ALL_LESSONS)

        val all = AchievementEvaluator.earned(none.copy(completedByLevel = totals))
        assertThat(all).containsAtLeast(
            Achievement.BEGINNER_COMPLETE, Achievement.INTERMEDIATE_COMPLETE, Achievement.ADVANCED_COMPLETE, Achievement.ALL_LESSONS,
        )
    }

    @Test
    fun emptyLevelIsNeverComplete() {
        val earned = AchievementEvaluator.earned(none.copy(totalByLevel = emptyMap()))
        assertThat(earned).containsNoneOf(Achievement.BEGINNER_COMPLETE, Achievement.ALL_LESSONS)
    }

    @Test
    fun quizAchievements() {
        assertThat(AchievementEvaluator.earned(none.copy(quizzesTaken = 1))).containsExactly(Achievement.FIRST_QUIZ)
        assertThat(AchievementEvaluator.earned(none.copy(quizzesTaken = 5, hasPerfectQuiz = true)))
            .containsExactly(Achievement.FIRST_QUIZ, Achievement.FIVE_QUIZZES, Achievement.PERFECT_QUIZ)
    }

    @Test
    fun streakThresholds() {
        assertThat(AchievementEvaluator.earned(none.copy(longestStreak = 2))).isEmpty()
        assertThat(AchievementEvaluator.earned(none.copy(longestStreak = 3))).containsExactly(Achievement.STREAK_3)
        assertThat(AchievementEvaluator.earned(none.copy(longestStreak = 7))).containsExactly(Achievement.STREAK_3, Achievement.STREAK_7)
        assertThat(AchievementEvaluator.earned(none.copy(longestStreak = 30)))
            .containsExactly(Achievement.STREAK_3, Achievement.STREAK_7, Achievement.STREAK_30)
    }

    @Test
    fun flashcardsAndDailyGoal() {
        assertThat(AchievementEvaluator.earned(none.copy(flashcardReviews = 49))).isEmpty()
        assertThat(AchievementEvaluator.earned(none.copy(flashcardReviews = 50))).containsExactly(Achievement.FLASHCARDS_50)
        assertThat(AchievementEvaluator.earned(none.copy(bestDayActivity = 2))).isEmpty()
        assertThat(AchievementEvaluator.earned(none.copy(bestDayActivity = 3))).containsExactly(Achievement.DAILY_GOAL)
        assertThat(AchievementEvaluator.earned(none.copy(bestDayActivity = 5, dailyGoal = 0))).isEmpty()
    }

    @Test
    fun newlyEarnedSkipsAlreadyUnlockedInDeclarationOrder() {
        val inputs = none.copy(quizzesTaken = 6, longestStreak = 3, completedByLevel = mapOf("beginner" to 2))
        val fresh = AchievementEvaluator.newlyEarned(inputs, alreadyUnlocked = setOf("first_quiz"))
        assertThat(fresh).containsExactly(Achievement.FIRST_LESSON, Achievement.FIVE_QUIZZES, Achievement.STREAK_3).inOrder()
    }
}
