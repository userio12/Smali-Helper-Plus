package com.riyadm.smalihelperplus.domain.learning

/** Visual hint for an achievement badge; mapped to an icon by the UI. */
enum class AchievementIcon { LESSON, LEVEL, QUIZ, STREAK, FLASHCARD, GOAL, TROPHY }

enum class Achievement(val id: String, val title: String, val description: String, val icon: AchievementIcon) {
    FIRST_LESSON("first_lesson", "First steps", "Complete your first lesson.", AchievementIcon.LESSON),
    BEGINNER_COMPLETE("beginner_complete", "Beginner graduate", "Complete every beginner lesson.", AchievementIcon.LEVEL),
    INTERMEDIATE_COMPLETE("intermediate_complete", "Intermediate graduate", "Complete every intermediate lesson.", AchievementIcon.LEVEL),
    ADVANCED_COMPLETE("advanced_complete", "Advanced graduate", "Complete every advanced lesson.", AchievementIcon.LEVEL),
    ALL_LESSONS("all_lessons", "Smali scholar", "Complete every lesson in the academy.", AchievementIcon.TROPHY),
    FIRST_QUIZ("first_quiz", "Quiz taker", "Finish your first quiz.", AchievementIcon.QUIZ),
    PERFECT_QUIZ("perfect_quiz", "Flawless", "Answer every question of a quiz correctly.", AchievementIcon.QUIZ),
    FIVE_QUIZZES("five_quizzes", "Practice makes perfect", "Finish 5 quizzes.", AchievementIcon.QUIZ),
    STREAK_3("streak_3", "Warming up", "Study 3 days in a row.", AchievementIcon.STREAK),
    STREAK_7("streak_7", "One-week streak", "Study 7 days in a row.", AchievementIcon.STREAK),
    STREAK_30("streak_30", "Unstoppable", "Study 30 days in a row.", AchievementIcon.STREAK),
    FLASHCARDS_50("flashcards_50", "Card shark", "Review 50 flashcards.", AchievementIcon.FLASHCARD),
    DAILY_GOAL("daily_goal", "Goal getter", "Reach your daily goal.", AchievementIcon.GOAL),
    ;

    companion object {
        fun fromId(id: String): Achievement? = entries.firstOrNull { it.id == id }
    }
}

/** Facts the achievement rules are evaluated against. */
data class AchievementInputs(
    /** Completed lesson count per level id ("beginner", "intermediate", "advanced"). */
    val completedByLevel: Map<String, Int>,
    /** Total lesson count per level id. */
    val totalByLevel: Map<String, Int>,
    val quizzesTaken: Int,
    val hasPerfectQuiz: Boolean,
    val longestStreak: Int,
    val flashcardReviews: Int,
    /** Highest activity count on any single study day. */
    val bestDayActivity: Int,
    val dailyGoal: Int,
)

/** Pure rules deciding which achievements are earned. */
object AchievementEvaluator {

    fun earned(inputs: AchievementInputs): Set<Achievement> = buildSet {
        val completed = inputs.completedByLevel.values.sum()
        val total = inputs.totalByLevel.values.sum()
        fun levelDone(level: String): Boolean {
            val t = inputs.totalByLevel[level] ?: 0
            return t > 0 && (inputs.completedByLevel[level] ?: 0) >= t
        }
        if (completed >= 1) add(Achievement.FIRST_LESSON)
        if (levelDone(LessonLevel.BEGINNER.id)) add(Achievement.BEGINNER_COMPLETE)
        if (levelDone(LessonLevel.INTERMEDIATE.id)) add(Achievement.INTERMEDIATE_COMPLETE)
        if (levelDone(LessonLevel.ADVANCED.id)) add(Achievement.ADVANCED_COMPLETE)
        if (total > 0 && completed >= total) add(Achievement.ALL_LESSONS)
        if (inputs.quizzesTaken >= 1) add(Achievement.FIRST_QUIZ)
        if (inputs.hasPerfectQuiz) add(Achievement.PERFECT_QUIZ)
        if (inputs.quizzesTaken >= 5) add(Achievement.FIVE_QUIZZES)
        if (inputs.longestStreak >= 3) add(Achievement.STREAK_3)
        if (inputs.longestStreak >= 7) add(Achievement.STREAK_7)
        if (inputs.longestStreak >= 30) add(Achievement.STREAK_30)
        if (inputs.flashcardReviews >= 50) add(Achievement.FLASHCARDS_50)
        if (inputs.dailyGoal > 0 && inputs.bestDayActivity >= inputs.dailyGoal) add(Achievement.DAILY_GOAL)
    }

    /** Achievements earned by [inputs] that are not in [alreadyUnlocked] (ids), in declaration order. */
    fun newlyEarned(inputs: AchievementInputs, alreadyUnlocked: Set<String>): List<Achievement> =
        earned(inputs).filter { it.id !in alreadyUnlocked }.sortedBy { it.ordinal }
}
