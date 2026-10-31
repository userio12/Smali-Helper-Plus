package com.riyadm.smalihelperplus.domain.learning

/** How well the learner recalled a flashcard. */
enum class ReviewRating { AGAIN, HARD, GOOD, EASY }

/** Leitner-box state of one card. [box] is 1..[LeitnerScheduler.MAX_BOX]; new cards have no state yet. */
data class LeitnerState(val box: Int, val nextReviewEpochDay: Long, val reviewCount: Int)

/**
 * Pure Leitner-box spaced repetition.
 *
 * Boxes 1..5 are reviewed after 0, 1, 3, 7 and 14 days respectively:
 * - AGAIN: back to box 1, due again today (so it returns later in the same session);
 * - HARD: stays in its box (new cards enter box 1) and is due no sooner than tomorrow;
 * - GOOD: moves up one box;
 * - EASY: moves up two boxes.
 */
object LeitnerScheduler {
    const val MIN_BOX = 1
    const val MAX_BOX = 5

    /** Review interval in days for boxes 1..5. */
    val INTERVAL_DAYS: List<Int> = listOf(0, 1, 3, 7, 14)

    fun intervalFor(box: Int): Int = INTERVAL_DAYS[box.coerceIn(MIN_BOX, MAX_BOX) - 1]

    /** A card without state (never reviewed) is always due. */
    fun isDue(state: LeitnerState?, todayEpochDay: Long): Boolean =
        state == null || state.nextReviewEpochDay <= todayEpochDay

    fun review(state: LeitnerState?, rating: ReviewRating, todayEpochDay: Long): LeitnerState {
        val current = state?.box?.coerceIn(MIN_BOX, MAX_BOX)
        val newBox = when (rating) {
            ReviewRating.AGAIN -> MIN_BOX
            ReviewRating.HARD -> current ?: MIN_BOX
            // A new card counts as sitting in box 1 before its first review.
            ReviewRating.GOOD -> (current ?: MIN_BOX) + 1
            ReviewRating.EASY -> (current ?: MIN_BOX) + 2
        }.coerceIn(MIN_BOX, MAX_BOX)
        val interval = when (rating) {
            ReviewRating.AGAIN -> 0
            ReviewRating.HARD -> maxOf(1, intervalFor(newBox))
            ReviewRating.GOOD, ReviewRating.EASY -> intervalFor(newBox)
        }
        return LeitnerState(
            box = newBox,
            nextReviewEpochDay = todayEpochDay + interval,
            reviewCount = (state?.reviewCount ?: 0) + 1,
        )
    }
}
