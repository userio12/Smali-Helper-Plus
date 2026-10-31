package com.riyadm.smalihelperplus

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class LearningTest : BaseUiTest() {

    private val firstLesson = "what-is-smali"

    private fun completedLessonsHeader(count: Int) =
        hasText("$count of ", substring = true) and hasText(" lessons", substring = true)

    /** The Learn list keeps its scroll position when coming back from a lesson, so scroll the header into view first. */
    private fun waitForCompletedLessons(count: Int) {
        scrollTo("learn_list", "learn_header")
        waitFor { composeRule.onAllNodes(completedLessonsHeader(count)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openLearn() {
        openTab("nav_learn")
        waitForTag("learn_list")
    }

    private fun openFirstLesson() {
        openLearn()
        scrollToAndClick("learn_list", "learn_lesson_$firstLesson")
        waitForTag("lesson_list")
    }

    /** Answers every question with the first option (right or wrong) and waits for the result screen. Returns the count. */
    private fun answerWholeQuiz(): Int {
        waitForTag("quiz_counter")
        val total = Regex("of (\\d+)").find(textOf("quiz_counter"))!!.groupValues[1].toInt()
        for (number in 1..total) {
            waitForText("Question $number of $total")
            waitForTag("quiz_option_0")
            composeRule.onNodeWithTag("quiz_option_0").performClick()
            waitFor { runCatching { composeRule.onNodeWithTag("quiz_next").assertIsEnabled() }.isSuccess }
            composeRule.onNodeWithTag("quiz_next").performClick()
        }
        waitForTag("quiz_result")
        return total
    }

    @Test
    fun learnScreen_listsLessonsAndStartsAtZeroProgress() {
        openLearn()
        waitForTag("learn_continue")
        waitForCompletedLessons(0)
        scrollTo("learn_list", "learn_lesson_$firstLesson")
        composeRule.onNodeWithTag("learn_lesson_$firstLesson").assertExists()
    }

    @Test
    fun markingLessonComplete_updatesProgressOnLearnScreen() {
        openFirstLesson()
        scrollToAndClick("lesson_list", "lesson_complete")
        scrollTo("lesson_list", "lesson_completed_banner")

        pressSystemBack()
        waitForTag("learn_list")
        waitForCompletedLessons(1)
        // "Continue learning" moves on to the next lesson.
        scrollTo("learn_list", "learn_continue")
        waitForText("The Android Runtime")
    }

    @Test
    fun completedLesson_canBeMarkedNotDone() {
        openFirstLesson()
        scrollToAndClick("lesson_list", "lesson_complete")
        scrollToAndClick("lesson_list", "lesson_mark_incomplete")
        scrollTo("lesson_list", "lesson_complete")

        pressSystemBack()
        waitForTag("learn_list")
        waitForCompletedLessons(0)
    }

    @Test
    fun lessonQuiz_answerAllQuestions_showsScore() {
        openFirstLesson()
        scrollToAndClick("lesson_list", "lesson_quiz")

        val total = answerWholeQuiz()

        waitForText("of $total correct", substring = true)
        val score = textOf("quiz_score")
        assertThat(score).matches("\\d+ of $total correct")
        waitForTag("quiz_retry")
    }

    @Test
    fun lessonQuiz_resultIsRememberedOnTheLesson() {
        openFirstLesson()
        scrollToAndClick("lesson_list", "lesson_quiz")
        answerWholeQuiz()
        // "Saving result…" disappears once the score is stored; then the lesson offers a retake with the best score.
        clickTag("quiz_back")
        waitForTag("lesson_list")
        scrollTo("lesson_list", "lesson_quiz")
        waitForText("best", substring = true)
    }

    @Test
    fun mixedQuiz_canBeCompleted() {
        openLearn()
        clickTag("learn_mixed_quiz")

        val total = answerWholeQuiz()

        assertThat(total).isGreaterThan(0)
        assertThat(textOf("quiz_score")).matches("\\d+ of $total correct")
    }

    @Test
    fun flashcards_canBeStartedAndFlipped() {
        openLearn()
        clickTag("learn_flashcards")
        clickTag("flashcard_start")
        clickTag("flashcard_flip")
        // The card is one clickable (merged) node, so its answer side only shows up in the unmerged tree.
        composeRule.onNodeWithTag("flashcard_card").assert(hasStateDescription("Answer side"))
        waitFor { composeRule.onAllNodes(hasTestTag("flashcard_back"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("flashcard_back", useUnmergedTree = true).assertIsDisplayed()
    }
}
