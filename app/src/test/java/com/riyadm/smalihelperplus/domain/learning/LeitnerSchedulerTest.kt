package com.riyadm.smalihelperplus.domain.learning

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LeitnerSchedulerTest {
    private val today = 20_000L

    @Test
    fun newCardIsDue() {
        assertThat(LeitnerScheduler.isDue(null, today)).isTrue()
    }

    @Test
    fun dueWhenNextReviewIsTodayOrEarlier() {
        assertThat(LeitnerScheduler.isDue(LeitnerState(2, today, 1), today)).isTrue()
        assertThat(LeitnerScheduler.isDue(LeitnerState(2, today - 3, 1), today)).isTrue()
        assertThat(LeitnerScheduler.isDue(LeitnerState(2, today + 1, 1), today)).isFalse()
    }

    @Test
    fun goodOnNewCardMovesToBoxTwoDueTomorrow() {
        val s = LeitnerScheduler.review(null, ReviewRating.GOOD, today)
        assertThat(s).isEqualTo(LeitnerState(box = 2, nextReviewEpochDay = today + 1, reviewCount = 1))
    }

    @Test
    fun goodClimbsThroughAllIntervals() {
        var state: LeitnerState? = LeitnerState(1, today, 0)
        val expectedIntervals = listOf(1, 3, 7, 14, 14)
        expectedIntervals.forEachIndexed { i, interval ->
            state = LeitnerScheduler.review(state, ReviewRating.GOOD, today)
            assertThat(state!!.box).isEqualTo(minOf(2 + i, 5))
            assertThat(state!!.nextReviewEpochDay - today).isEqualTo(interval.toLong())
        }
        assertThat(state!!.reviewCount).isEqualTo(5)
    }

    @Test
    fun againResetsToBoxOneDueToday() {
        val s = LeitnerScheduler.review(LeitnerState(5, today, 9), ReviewRating.AGAIN, today)
        assertThat(s).isEqualTo(LeitnerState(1, today, 10))
        assertThat(LeitnerScheduler.isDue(s, today)).isTrue()
    }

    @Test
    fun hardKeepsBoxAndWaitsAtLeastOneDay() {
        assertThat(LeitnerScheduler.review(null, ReviewRating.HARD, today)).isEqualTo(LeitnerState(1, today + 1, 1))
        assertThat(LeitnerScheduler.review(LeitnerState(3, today, 2), ReviewRating.HARD, today))
            .isEqualTo(LeitnerState(3, today + 3, 3))
    }

    @Test
    fun easySkipsABoxAndCapsAtFive() {
        assertThat(LeitnerScheduler.review(null, ReviewRating.EASY, today).box).isEqualTo(3)
        assertThat(LeitnerScheduler.review(LeitnerState(4, today, 1), ReviewRating.EASY, today))
            .isEqualTo(LeitnerState(5, today + 14, 2))
    }

    @Test
    fun outOfRangeBoxesAreClamped() {
        assertThat(LeitnerScheduler.review(LeitnerState(9, today, 0), ReviewRating.GOOD, today).box).isEqualTo(5)
        assertThat(LeitnerScheduler.review(LeitnerState(-2, today, 0), ReviewRating.HARD, today).box).isEqualTo(1)
        assertThat(LeitnerScheduler.intervalFor(0)).isEqualTo(0)
        assertThat(LeitnerScheduler.intervalFor(42)).isEqualTo(14)
    }
}
