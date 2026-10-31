package com.riyadm.smalihelperplus

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class OpcodeReferenceTest : BaseUiTest() {

    private fun assertTextShownSomewhere(text: String) {
        waitForText(text)
        composeRule.onAllNodesWithText(text).onFirst().assertExists()
    }

    private fun toggleBookmark() = composeRule.onNodeWithTag("opcode_bookmark").performClick()

    private fun assertBookmarkIcon(description: String) {
        waitFor { runCatching { composeRule.onNodeWithTag("opcode_bookmark").assertContentDescriptionEquals(description) }.isSuccess }
    }

    /** Leaves a detail screen opened through [openOpcode] and lands on the Home tab. */
    private fun backToHome() {
        pressSystemBack()
        waitForTag("opcode_list")
        pressSystemBack()
        // The grid keeps its scroll position, so "All instructions" may be scrolled away.
        waitForTag("categories_grid")
        openTab("nav_home")
    }

    @Test
    fun categories_showAllInstructionsAndCategoryCards() {
        openTab("nav_opcodes")
        assertTagDisplayed("categories_all")
        scrollTo("categories_grid", "category_move")
        assertTagDisplayed("category_move")
        scrollTo("categories_grid", "category_constant")
        assertTagDisplayed("category_constant")
    }

    @Test
    fun openingCategory_showsItsInstructions() {
        openTab("nav_opcodes")
        openCategory("category_constant")
        assertTagDisplayed("opcode_list")
        assertTextShownSomewhere("Constants")
        scrollTo("opcode_list", "opcode_item_const/4")
        composeRule.onNodeWithTag("opcode_item_const/4").assertExists()
    }

    @Test
    fun allInstructions_canBeFilteredByName() {
        openTab("nav_opcodes")
        clickTag("categories_all")
        replaceText("opcode_filter", "invoke-static")
        scrollTo("opcode_list", "opcode_item_invoke-static")
        composeRule.onNodeWithTag("opcode_item_invoke-static").assertExists()
    }

    @Test
    fun opcodeDetail_showsOpcodeValueFormatAndActions() {
        openOpcode("const/4", "category_constant")
        assertTextShownSomewhere("0x12")
        assertTextShownSomewhere("Format 11n")
        assertTextShownSomewhere("Syntax")
        composeRule.onNodeWithTag("opcode_copy").assertIsDisplayed()
        composeRule.onNodeWithTag("opcode_share").assertIsDisplayed()
    }

    @Test
    fun bookmark_toggleAddsToBookmarksAndSecondTapRemoves() {
        openOpcode("const/4", "category_constant")
        assertBookmarkIcon("Add bookmark")
        toggleBookmark()
        assertBookmarkIcon("Remove bookmark")

        backToHome()
        clickTag("home_bookmarks")
        assertTagDisplayed("bookmarks_list")
        // The bookmark opens the opcode again, where a second tap removes it.
        clickTag("bookmark_opcode_const/4")
        waitForTag("opcode_bookmark")
        assertBookmarkIcon("Remove bookmark")
        toggleBookmark()
        assertBookmarkIcon("Add bookmark")
    }

    @Test
    fun relatedOpcode_opensItsDetail() {
        openOpcode("const/4", "category_constant")
        waitForTag("related_const/16")
        composeRule.onNodeWithTag("related_const/16").performScrollTo().performClick()
        // const/16 is opcode 0x13 with format 21s.
        assertTextShownSomewhere("0x13")
        assertTextShownSomewhere("Format 21s")
    }

    @Test
    fun openedOpcode_isRecordedInRecentlyViewed() {
        openOpcode("const/4", "category_constant")
        backToHome()
        clickTag("home_history")
        assertTagDisplayed("history_list")
        assertTagDisplayed("history_opcode_const/4")
    }
}
