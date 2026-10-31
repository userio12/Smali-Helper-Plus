package com.riyadm.smalihelperplus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class SearchTest : BaseUiTest() {

    private fun openSearch() {
        clickTag("home_search")
        waitForTag("search_field")
    }

    /** Types [query] and waits until the result [name] is composed (scrolling if it is below the fold). */
    private fun searchAndExpect(query: String, name: String) {
        replaceText("search_field", query)
        waitForTag("search_results")
        scrollTo("search_results", "search_result_$name")
        composeRule.onNodeWithTag("search_result_$name").assertIsDisplayed()
    }

    @Test
    fun exactName_showsResult() {
        openSearch()
        searchAndExpect("invoke-static", "invoke-static")
    }

    @Test
    fun typo_stillFindsTheOpcode() {
        openSearch()
        searchAndExpect("invok-static", "invoke-static")
    }

    @Test
    fun formatQuery_listsOpcodesOfThatFormat() {
        openSearch()
        // invoke-virtual is encoded in format 35c.
        searchAndExpect("35c", "invoke-virtual")
    }

    @Test
    fun nonsenseQuery_showsNoResults() {
        openSearch()
        replaceText("search_field", "zzqqxxjj")
        assertTextDisplayed("No results")
    }

    @Test
    fun openingAResult_showsDetail() {
        openSearch()
        searchAndExpect("const-string", "const-string")
        composeRule.onNodeWithTag("search_result_const-string").performClick()
        waitForTag("opcode_bookmark")
    }

    @Test
    fun recentSearch_isSavedAndCanBeCleared() {
        openSearch()
        searchAndExpect("const-string", "const-string")
        // Opening a result records the query.
        composeRule.onNodeWithTag("search_result_const-string").performClick()
        waitForTag("opcode_bookmark")
        pressSystemBack()
        waitForTag("search_field")

        // Empty field => recent searches panel.
        waitFor { tagExists("search_recents") || tagExists("search_clear") }
        if (tagExists("search_clear")) composeRule.onNodeWithTag("search_clear").performClick()
        waitForTag("search_recents")
        assertTextDisplayed("const-string")

        clickTag("search_clear_history")
        clickTag("search_clear_history_confirm")
        waitForTagGone("search_recents")
        assertTextDisplayed("Search the reference")
    }
}
