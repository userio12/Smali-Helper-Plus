package com.riyadm.smalihelperplus.domain.learning

/** Pure streak math over the set of calendar days (epoch days) with learning activity. */
object StreakCalculator {

    /**
     * Consecutive active days ending today, or ending yesterday if there is no activity yet today
     * (so a streak is not shown as broken before the user had a chance to study today).
     */
    fun currentStreak(activeDays: Collection<Long>, todayEpochDay: Long): Int {
        if (activeDays.isEmpty()) return 0
        val days = activeDays.toHashSet()
        var day = if (todayEpochDay in days) todayEpochDay else todayEpochDay - 1
        var streak = 0
        while (day in days) {
            streak++
            day--
        }
        return streak
    }

    /** Longest run of consecutive active days. */
    fun longestStreak(activeDays: Collection<Long>): Int {
        if (activeDays.isEmpty()) return 0
        val sorted = activeDays.toSortedSet()
        var best = 1
        var run = 1
        var prev: Long? = null
        for (d in sorted) {
            if (prev != null) {
                run = if (d == prev + 1) run + 1 else 1
                if (run > best) best = run
            }
            prev = d
        }
        return best
    }
}

/** Streak-related helpers built on top of [StreakCalculator]. */
object StreakInsights {
    /** True when the learner studied yesterday but not yet today, so the current streak ends tonight. */
    fun isAtRisk(activeDays: Collection<Long>, todayEpochDay: Long): Boolean =
        todayEpochDay !in activeDays && (todayEpochDay - 1) in activeDays

    /** Number of active days within the last [days] calendar days (including today). */
    fun activeDaysInLast(activeDays: Collection<Long>, todayEpochDay: Long, days: Int): Int {
        if (days <= 0) return 0
        val from = todayEpochDay - days + 1
        return activeDays.toHashSet().count { it in from..todayEpochDay }
    }
}

/** One cell of the activity heatmap. [epochDay] is null for padding cells after today. */
data class HeatmapCell(val epochDay: Long?, val count: Int)

/**
 * Builds a GitHub-style activity grid: [weeks] columns of 7 days (Monday first), the last column containing today.
 * Days after today in the final week are returned as padding cells.
 */
object ActivityHeatmap {
    fun build(activity: Map<Long, Int>, todayEpochDay: Long, weeks: Int = 12): List<List<HeatmapCell>> {
        require(weeks > 0)
        // 1970-01-01 (epoch day 0) was a Thursday; ISO day-of-week index with Monday = 0.
        val todayIndex = Math.floorMod(todayEpochDay + 3, 7L).toInt()
        val firstMonday = todayEpochDay - todayIndex - (weeks - 1) * 7L
        return List(weeks) { w ->
            List(7) { d ->
                val day = firstMonday + w * 7L + d
                if (day > todayEpochDay) HeatmapCell(null, 0) else HeatmapCell(day, activity[day] ?: 0)
            }
        }
    }

    /** Intensity bucket 0..4 for a day's activity relative to the daily goal. */
    fun level(count: Int, dailyGoal: Int): Int {
        if (count <= 0) return 0
        val goal = dailyGoal.coerceAtLeast(1)
        return when {
            count >= goal * 2 -> 4
            count >= goal -> 3
            count * 2 >= goal -> 2
            else -> 1
        }
    }
}
