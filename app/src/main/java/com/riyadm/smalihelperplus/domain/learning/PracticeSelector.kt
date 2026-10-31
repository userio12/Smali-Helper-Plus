package com.riyadm.smalihelperplus.domain.learning

import kotlin.random.Random

/** Builds practice question sets from the questions of all lessons. Pure and deterministic for a given seed. */
object PracticeSelector {
    const val DAILY_COUNT = 8
    const val MIXED_COUNT = 10

    /**
     * Deterministic daily selection: the same [epochDay] and inputs always give the same questions in the same order.
     *
     * Questions are drawn round-robin across lessons so a session covers several topics. Lessons the learner has
     * started or completed ([startedLessonIds]) are used first; if they cannot fill the session, the remaining
     * questions come from not-yet-started lessons in curriculum order ([lessonOrder]), so newcomers practise the
     * beginner material first.
     */
    fun daily(
        questions: List<QuizItem>,
        startedLessonIds: Set<String>,
        lessonOrder: Map<String, Int>,
        epochDay: Long,
        count: Int = DAILY_COUNT,
    ): List<QuizItem> {
        if (questions.isEmpty() || count <= 0) return emptyList()
        val random = Random(epochDay * 31 + 17)
        // Sorting makes the result independent of the input order.
        val byLesson = questions.sortedBy { it.question.id }.groupBy { it.lessonId }
        val started = byLesson.keys.filter { it in startedLessonIds }.sortedWith(lessonComparator(lessonOrder))
        val notStarted = byLesson.keys.filter { it !in startedLessonIds }.sortedWith(lessonComparator(lessonOrder))

        val picked = ArrayList<QuizItem>(count)
        picked += roundRobin(started.shuffled(random).map { byLesson.getValue(it).shuffled(random) }, count)
        if (picked.size < count) {
            // Keep curriculum order for lessons the learner has not reached yet.
            picked += roundRobin(notStarted.map { byLesson.getValue(it).shuffled(random) }, count - picked.size)
        }
        return picked.shuffled(random)
    }

    /** Random mixed quiz across all lessons, drawn round-robin so it spans many lessons. */
    fun mixed(questions: List<QuizItem>, random: Random, count: Int = MIXED_COUNT): List<QuizItem> {
        if (questions.isEmpty() || count <= 0) return emptyList()
        val groups = questions.groupBy { it.lessonId }.values.shuffled(random).map { it.shuffled(random) }
        return roundRobin(groups, count).shuffled(random)
    }

    private fun lessonComparator(order: Map<String, Int>) =
        compareBy<String>({ order[it] ?: Int.MAX_VALUE }, { it })

    private fun roundRobin(groups: List<List<QuizItem>>, limit: Int): List<QuizItem> {
        val out = ArrayList<QuizItem>(limit)
        var round = 0
        while (out.size < limit) {
            var added = false
            for (g in groups) {
                if (round < g.size) {
                    out += g[round]
                    added = true
                    if (out.size == limit) break
                }
            }
            if (!added) break
            round++
        }
        return out
    }
}
