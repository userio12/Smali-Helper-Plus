package com.riyadm.smalihelperplus.domain.learning

import com.riyadm.smalihelperplus.core.content.QuizQuestionDto

enum class LessonLevel(val id: String, val title: String) {
    BEGINNER("beginner", "Beginner"),
    INTERMEDIATE("intermediate", "Intermediate"),
    ADVANCED("advanced", "Advanced"),
    ;

    companion object {
        fun fromId(id: String): LessonLevel? = entries.firstOrNull { it.id == id }
    }
}

enum class LessonProgressStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

/** A lesson as listed in the academy, joined with the learner's progress. */
data class LessonSummary(
    val id: String,
    val level: LessonLevel,
    val order: Int,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int,
    val status: LessonProgressStatus,
    /** Best quiz score in percent, or null if the lesson quiz was never taken. */
    val bestQuizScore: Int?,
    val lastOpenedAtMillis: Long?,
    val completedAtMillis: Long?,
)

/** A quiz question together with the lesson it belongs to. */
data class QuizItem(val lessonId: String, val lessonTitle: String, val question: QuizQuestionDto)

data class FlashcardItem(
    val id: String,
    val topic: String,
    val front: String,
    val back: String,
    val code: String?,
    /** Leitner box 1..5, or 0 for a card that was never reviewed. */
    val box: Int,
    val reviewCount: Int,
    val isDue: Boolean,
)

/** Compact overview used by the Learn tab header and the Home screen. */
data class LearningSummary(
    val completedLessons: Int,
    val totalLessons: Int,
    /** Lesson to resume: the most recently opened unfinished lesson, else the first lesson not yet completed. */
    val continueLesson: LessonSummary?,
    val currentStreak: Int,
    val longestStreak: Int,
    val todayActivity: Int,
    val dailyGoal: Int,
    /** Average score (percent) over all quizzes taken, or null when none were taken. */
    val averageQuizScore: Int?,
    val quizzesTaken: Int,
    val dueFlashcards: Int,
    val dailyPracticeDoneToday: Boolean,
) {
    val progressFraction: Float get() = if (totalLessons == 0) 0f else completedLessons.toFloat() / totalLessons
    val dailyGoalMet: Boolean get() = dailyGoal > 0 && todayActivity >= dailyGoal
}

data class AchievementState(val achievement: Achievement, val unlockedAtMillis: Long?) {
    val unlocked: Boolean get() = unlockedAtMillis != null
}

data class QuizHistoryEntry(
    val id: Long,
    val lessonId: String,
    val title: String,
    val correct: Int,
    val total: Int,
    val takenAtMillis: Long,
) {
    val percent: Int get() = scorePercent(correct, total)
}

data class LevelProgress(val level: LessonLevel, val completed: Int, val total: Int)

/** Everything shown on the learning progress screen. */
data class LearningProgressStats(
    val levels: List<LevelProgress>,
    val averageQuizScore: Int?,
    val quizzesTaken: Int,
    val flashcardReviews: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val todayEpochDay: Long,
    /** Activity count per epoch day. */
    val studyDays: Map<Long, Int>,
    val achievements: List<AchievementState>,
    val history: List<QuizHistoryEntry>,
)

/** Result of finishing a quiz. */
data class QuizOutcome(
    val correct: Int,
    val total: Int,
    val isNewBest: Boolean,
    val newAchievements: List<Achievement>,
) {
    val percent: Int get() = scorePercent(correct, total)
}

fun scorePercent(correct: Int, total: Int): Int = if (total <= 0) 0 else (correct * 100 + total / 2) / total
