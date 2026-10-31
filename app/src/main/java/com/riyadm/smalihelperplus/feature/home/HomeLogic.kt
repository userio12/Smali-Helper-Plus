package com.riyadm.smalihelperplus.feature.home

import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonProgressEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonStatus
import com.riyadm.smalihelperplus.core.database.entity.QuizResultEntity
import com.riyadm.smalihelperplus.domain.learning.StreakCalculator
import kotlin.math.roundToInt

/** What the "Continue learning" card shows. */
sealed interface ContinueLearning {
    /** [inProgress] is true if the lesson was started but not completed. */
    data class Next(val lesson: LessonEntity, val inProgress: Boolean) : ContinueLearning

    /** Every lesson is completed. */
    data object AllDone : ContinueLearning

    /** No lessons are installed yet. */
    data object None : ContinueLearning
}

data class LearningStats(
    val lessonsCompleted: Int = 0,
    val lessonsTotal: Int = 0,
    val streak: Int = 0,
    val quizzesTaken: Int = 0,
    /** Average quiz score in percent, or null if no quiz was taken. */
    val averageScorePercent: Int? = null,
) {
    val completedFraction: Float get() = if (lessonsTotal == 0) 0f else lessonsCompleted.toFloat() / lessonsTotal
}

object HomeLogic {

    fun greetingFor(hourOfDay: Int): String = when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Good night"
    }

    /**
     * Picks the lesson to continue: the most recently opened in-progress lesson, else the first (lowest sort order)
     * lesson that has not been started or completed.
     */
    fun continueLearning(lessons: List<LessonEntity>, progress: List<LessonProgressEntity>): ContinueLearning {
        if (lessons.isEmpty()) return ContinueLearning.None
        val byId = progress.associateBy { it.lessonId }
        val ordered = lessons.sortedBy { it.sortOrder }
        ordered
            .filter { byId[it.id]?.status == LessonStatus.IN_PROGRESS }
            .maxByOrNull { byId.getValue(it.id).lastOpenedAtMillis }
            ?.let { return ContinueLearning.Next(it, inProgress = true) }
        ordered
            .firstOrNull { (byId[it.id]?.status ?: LessonStatus.NOT_STARTED) == LessonStatus.NOT_STARTED }
            ?.let { return ContinueLearning.Next(it, inProgress = false) }
        return ContinueLearning.AllDone
    }

    fun stats(
        lessons: List<LessonEntity>,
        progress: List<LessonProgressEntity>,
        quizResults: List<QuizResultEntity>,
        studyEpochDays: Collection<Long>,
        todayEpochDay: Long,
    ): LearningStats {
        val lessonIds = lessons.mapTo(HashSet()) { it.id }
        val scored = quizResults.filter { it.total > 0 }
        return LearningStats(
            lessonsCompleted = progress.count { it.status == LessonStatus.COMPLETED && it.lessonId in lessonIds },
            lessonsTotal = lessons.size,
            streak = StreakCalculator.currentStreak(studyEpochDays, todayEpochDay),
            quizzesTaken = quizResults.size,
            averageScorePercent = if (scored.isEmpty()) {
                null
            } else {
                scored.map { it.correct * 100.0 / it.total }.average().roundToInt()
            },
        )
    }
}
