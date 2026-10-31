package com.riyadm.smalihelperplus

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Shared set-up for the UI tests: Hilt (order 0), a reset of process-wide state (order 1) and the real [MainActivity]
 * (order 2). The app seeds Room from its assets while the splash screen is shown, so [waitForApp] waits for the Home
 * content instead of sleeping.
 */
abstract class BaseUiTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val stateRule = AppStateRule()
    @get:Rule(order = 2) val composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    /** On failure, logs the semantics tree (tag `UiTestFailure`) while the activity is still alive. */
    @get:Rule(order = 3) val dumpOnFailure = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description) {
            runCatching { composeRule.onRoot(useUnmergedTree = true).printToLog("UiTestFailure") }
        }
    }

    @Before
    fun waitForApp() {
        hiltRule.inject()
        // The featured card only exists once Room has been seeded and the dashboard has loaded.
        waitForTag("home_featured_opcode")
    }

    // ---- waiting -------------------------------------------------------------------------------------------------

    protected fun tagExists(tag: String): Boolean =
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    protected fun textExists(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    protected fun waitFor(timeoutMillis: Long = TIMEOUT_MS, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) { condition() }
    }

    protected fun waitForTag(tag: String, timeoutMillis: Long = TIMEOUT_MS) = waitFor(timeoutMillis) { tagExists(tag) }

    protected fun waitForText(text: String, substring: Boolean = false, timeoutMillis: Long = TIMEOUT_MS) =
        waitFor(timeoutMillis) { textExists(text, substring) }

    protected fun waitForTagGone(tag: String, timeoutMillis: Long = TIMEOUT_MS) = waitFor(timeoutMillis) { !tagExists(tag) }

    // ---- actions -------------------------------------------------------------------------------------------------

    /** Waits for the node, scrolls it into view when it sits in a scrollable container, then clicks it. */
    protected fun clickTag(tag: String) {
        waitForTag(tag)
        val node = composeRule.onNodeWithTag(tag)
        runCatching { node.performScrollTo() } // no scrollable parent: already where it will ever be
        node.performClick()
    }

    /**
     * Scrolls [listTag] until [itemTag] is composed, then clicks it. The click goes through the node's OnClick semantics
     * action: `performScrollToNode` may stop with the item under the (edge-to-edge) top app bar, where a coordinate
     * click would hit the bar instead.
     */
    protected fun scrollToAndClick(listTag: String, itemTag: String) {
        scrollTo(listTag, itemTag)
        composeRule.onNodeWithTag(itemTag).performSemanticsAction(SemanticsActions.OnClick)
    }

    /**
     * Scrolls [listTag] until [itemTag] is composed. Retries until the timeout because lists are often filled
     * asynchronously (debounced search, Room flows) and the item may not be in the list yet.
     */
    protected fun scrollTo(listTag: String, itemTag: String) {
        waitForTag(listTag)
        var lastError: Throwable? = null
        runCatching {
            waitFor {
                runCatching { composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(itemTag)) }
                    .onFailure { lastError = it }
                    .isSuccess
            }
        }.onFailure { throw lastError ?: it }
    }

    protected fun replaceText(tag: String, text: String) {
        waitForTag(tag)
        composeRule.onNodeWithTag(tag).performTextReplacement(text)
    }

    /** Opens a bottom navigation tab (`nav_home`, `nav_opcodes`, ...). */
    protected fun openTab(tag: String) = clickTag(tag)

    /** Goes back the way the system back gesture / button does. */
    protected fun pressSystemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    protected fun clickNavigateBack() {
        composeRule.onNodeWithContentDescription("Navigate back").performClick()
    }

    /** From Home: overflow menu, then the entry with [menuTag] (`menu_settings`, `menu_updates`, `menu_about`). */
    protected fun openHomeMenuEntry(menuTag: String) {
        openTab("nav_home")
        clickTag("home_menu")
        clickTag(menuTag)
    }

    protected fun openOpcode(name: String, categoryTag: String) {
        openTab("nav_opcodes")
        openCategory(categoryTag)
        scrollToAndClick("opcode_list", "opcode_item_$name")
        waitForTag("opcode_bookmark")
    }

    /** From the Opcodes tab: scrolls the grid (the category cards sit below the "Smali basics" section) and opens one. */
    protected fun openCategory(categoryTag: String) = scrollToAndClick("categories_grid", categoryTag)

    // ---- reading -------------------------------------------------------------------------------------------------

    protected fun assertTagDisplayed(tag: String) {
        waitForTag(tag)
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    protected fun assertTextDisplayed(text: String) {
        waitForText(text)
        composeRule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    /** Text shown by a text field (the editor, search field, ...) or text node with [tag]. */
    protected fun textOf(tag: String): String {
        val config = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
    }

    protected fun hasTextContaining(text: String): SemanticsMatcher = hasText(text, substring = true)

    companion object {
        /** Generous: the first launch seeds ~230 opcodes and 28 lessons, and CI emulators are slow. */
        const val TIMEOUT_MS = 20_000L
    }
}
