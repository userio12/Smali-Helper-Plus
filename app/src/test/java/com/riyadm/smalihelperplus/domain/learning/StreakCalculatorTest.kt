package com.riyadm.smalihelperplus.domain.learning

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 10, 30).toEpochDay()

    @Test
    fun currentStreakCountsBackFromTodayOrYesterday() {
        assertThat(StreakCalculator.currentStreak(listOf(today, today - 1, today - 2), today)).isEqualTo(3)
        assertThat(StreakCalculator.currentStreak(listOf(today - 1, today - 2), today)).isEqualTo(2)
        assertThat(StreakCalculator.currentStreak(listOf(today - 2, today - 3), today)).isEqualTo(0)
        assertThat(StreakCalculator.currentStreak(emptyList(), today)).isEqualTo(0)
    }

    @Test
    fun longestStreak() {
        assertThat(StreakCalculator.longestStreak(listOf(1L, 2L, 3L, 7L, 8L, 3L))).isEqualTo(3)
        assertThat(StreakCalculator.longestStreak(listOf(5L))).isEqualTo(1)
        assertThat(StreakCalculator.longestStreak(emptyList())).isEqualTo(0)
    }

    @Test
    fun atRiskOnlyWhenYesterdayActiveAndTodayNot() {
        assertThat(StreakInsights.isAtRisk(listOf(today - 1), today)).isTrue()
        assertThat(StreakInsights.isAtRisk(listOf(today - 1, today), today)).isFalse()
        assertThat(StreakInsights.isAtRisk(listOf(today - 2), today)).isFalse()
    }

    @Test
    fun activeDaysInWindow() {
        val days = listOf(today, today - 6, today - 7, today - 30, today + 1)
        assertThat(StreakInsights.activeDaysInLast(days, today, 7)).isEqualTo(2)
        assertThat(StreakInsights.activeDaysInLast(days, today, 8)).isEqualTo(3)
        assertThat(StreakInsights.activeDaysInLast(days, today, 0)).isEqualTo(0)
    }

    @Test
    fun heatmapIsMondayAlignedAndEndsWithToday() {
        val grid = ActivityHeatmap.build(mapOf(today to 4, today - 1 to 1), today, weeks = 12)
        assertThat(grid).hasSize(12)
        grid.forEach { assertThat(it).hasSize(7) }
        val first = grid.first().first().epochDay!!
        assertThat(LocalDate.ofEpochDay(first).dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        val lastWeek = grid.last()
        val todayIndex = LocalDate.ofEpochDay(today).dayOfWeek.value - 1 // 2026-10-30 is a Friday -> 4
        assertThat(todayIndex).isEqualTo(4)
        assertThat(lastWeek[todayIndex]).isEqualTo(HeatmapCell(today, 4))
        assertThat(lastWeek[todayIndex - 1]).isEqualTo(HeatmapCell(today - 1, 1))
        assertThat(lastWeek.drop(todayIndex + 1).all { it.epochDay == null }).isTrue()
    }

    @Test
    fun heatmapWorksForEveryWeekday() {
        for (offset in 0L..6L) {
            val day = today + offset
            val grid = ActivityHeatmap.build(emptyMap(), day, weeks = 2)
            val cells = grid.flatten().mapNotNull { it.epochDay }
            assertThat(cells.last()).isEqualTo(day)
            assertThat(LocalDate.ofEpochDay(cells.first()).dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
            assertThat(cells.zipWithNext().all { (a, b) -> b == a + 1 }).isTrue()
        }
    }

    @Test
    fun heatmapLevels() {
        assertThat(ActivityHeatmap.level(0, 3)).isEqualTo(0)
        assertThat(ActivityHeatmap.level(1, 3)).isEqualTo(1)
        assertThat(ActivityHeatmap.level(2, 3)).isEqualTo(2)
        assertThat(ActivityHeatmap.level(3, 3)).isEqualTo(3)
        assertThat(ActivityHeatmap.level(6, 3)).isEqualTo(4)
        assertThat(ActivityHeatmap.level(1, 0)).isEqualTo(3)
    }
}
