package com.riyadm.smalihelperplus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class NavigationTest : BaseUiTest() {

    @Test
    fun coldLaunch_showsHome() {
        assertTagDisplayed("home_search")
        assertTextDisplayed("Smali Helper Plus")
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
    }

    @Test
    fun eachBottomTab_opensItsScreen() {
        val tabs = listOf(
            "nav_opcodes" to "categories_all",
            "nav_learn" to "learn_list",
            "nav_playground" to "playground_editor",
            "nav_tools" to "tools_grid",
            "nav_home" to "home_search",
        )
        tabs.forEach { (tab, screenTag) ->
            openTab(tab)
            assertTagDisplayed(screenTag)
            composeRule.onNodeWithTag(tab).assertIsSelected()
        }
    }

    @Test
    fun systemBack_fromOpcodeList_returnsToCategories() {
        openTab("nav_opcodes")
        clickTag("categories_all")
        waitForTag("opcode_list")
        pressSystemBack()
        waitForTag("categories_all")
        waitForTagGone("opcode_list")
    }

    @Test
    fun toolbarBack_fromOpcodeDetail_returnsToList() {
        openOpcode("const/4", "category_constant")
        clickNavigateBack()
        waitForTag("opcode_list")
        waitForTagGone("opcode_bookmark")
    }

    @Test
    fun homeSearchBar_opensSearchAndBackReturnsHome() {
        clickTag("home_search")
        waitForTag("search_field")
        pressSystemBack()
        waitForTag("home_search")
        waitForTagGone("search_field")
    }

    @Test
    fun homeMenu_opensSettings_andBackReturnsHome() {
        openHomeMenuEntry("menu_settings")
        assertTagDisplayed("settings_list")
        pressSystemBack()
        waitForTag("home_search")
    }

    @Test
    fun homeMenu_opensUpdates() {
        openHomeMenuEntry("menu_updates")
        assertTagDisplayed("updates_list")
        assertTextDisplayed("Documentation updates")
    }

    @Test
    fun homeMenu_opensAbout() {
        openHomeMenuEntry("menu_about")
        assertTagDisplayed("about_list")
    }

    @Test
    fun homeShortcuts_openBookmarksAndHistory() {
        clickTag("home_bookmarks")
        assertTextDisplayed("No bookmarks yet")
        pressSystemBack()
        waitForTag("home_history")
        clickTag("home_history")
        assertTextDisplayed("Nothing viewed yet")
    }

    @Test
    fun toolsScreen_survivesActivityRecreation() {
        openTab("nav_tools")
        replaceText("tools_search", "hex")
        waitForTag("tool_number_converter")

        composeRule.activityRule.scenario.recreate()

        // Still on the Tools tab, with the filter text restored and applied.
        waitForTag("tools_grid")
        waitForTag("tool_number_converter")
        composeRule.onNodeWithTag("tools_search").assertTextContains("hex")
        composeRule.onNodeWithTag("nav_tools").assertIsSelected()
    }

    @Test
    fun secondaryScreenInput_survivesActivityRecreation() {
        openTab("nav_tools")
        clickTag("tool_number_converter")
        replaceText("numconv_input", "255")

        composeRule.activityRule.scenario.recreate()

        waitForTag("numconv_input")
        composeRule.onNodeWithTag("numconv_input").assertTextContains("255")
        // And back still works after the recreation.
        pressSystemBack()
        waitForTag("tools_grid")
    }
}
