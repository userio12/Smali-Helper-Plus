package com.riyadm.smalihelperplus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class PlaygroundTest : BaseUiTest() {

    private val unknownOpcodeCode = """
        .class public Lcom/acme/demo/Broken;
        .super Ljava/lang/Object;

        .method public static run()V
            .locals 2
            foo-bar v0, v1
            return-void
        .end method
    """.trimIndent()

    private val validCode = unknownOpcodeCode.replace("foo-bar v0, v1", "const/4 v0, 0x1")

    private val unformattedCode = """
        .class public Lcom/acme/demo/Fmt;
        .super Ljava/lang/Object;
        .method public static f()V
        .locals 1
        const/4 v0, 0x1
        return-void
        .end method
    """.trimIndent()

    @Before
    fun openPlayground() {
        openTab("nav_playground")
        waitForTag("playground_editor")
        // The starter sample is loaded asynchronously after the saved buffer was read.
        waitFor { textOf("playground_editor").contains("Greeter") }
    }

    private fun editorText() = textOf("playground_editor")

    private fun setEditor(code: String) {
        replaceText("playground_editor", code)
        waitFor { editorText() == code }
    }

    private fun waitForDiagnostics(matcher: (String) -> Boolean) = waitFor {
        composeRule.onAllNodes(hasTestTag("playground_diagnostics")).fetchSemanticsNodes().isNotEmpty() &&
            matcher(textOf("playground_diagnostics"))
    }

    private fun openMenuEntry(tag: String) {
        clickTag("playground_more")
        clickTag(tag)
    }

    @Test
    fun editor_startsWithTheStarterSample() {
        assertThat(editorText()).contains(".class public Lcom/acme/demo/Greeter;")
    }

    @Test
    fun typedSmali_isShownInTheEditor() {
        setEditor(validCode)
        assertThat(editorText()).contains("const/4 v0, 0x1")
    }

    @Test
    fun unknownOpcode_isReportedAsAnError_andClearsWhenFixed() {
        setEditor(unknownOpcodeCode)
        waitForDiagnostics { it.contains("error") }

        clickTag("playground_diagnostics")
        waitForTag("playground_diagnostics_sheet")
        waitForText("Unknown instruction", substring = true)

        // Editing stays possible semantically while the (modal) problems sheet is open.
        setEditor(validCode)
        waitForDiagnostics { !it.contains("error") }
    }

    @Test
    fun formatButton_reindentsTheCode() {
        setEditor(unformattedCode)
        clickTag("playground_format")
        waitFor { editorText() != unformattedCode }
        val line = editorText().lines().first { it.trim() == "const/4 v0, 0x1" }
        assertThat(line.first().isWhitespace()).isTrue()
    }

    @Test
    fun undoAndRedo_walkBetweenEditorStates() {
        composeRule.onNodeWithTag("playground_undo").assertIsNotEnabled()
        val starter = editorText()

        setEditor(validCode)
        composeRule.onNodeWithTag("playground_undo").assertIsEnabled()

        composeRule.onNodeWithTag("playground_undo").performClick()
        waitFor { editorText() == starter }
        composeRule.onNodeWithTag("playground_redo").assertIsEnabled()

        composeRule.onNodeWithTag("playground_redo").performClick()
        waitFor { editorText() == validCode }
    }

    @Test
    fun formatCanBeUndone() {
        setEditor(unformattedCode)
        clickTag("playground_format")
        waitFor { editorText() != unformattedCode }
        composeRule.onNodeWithTag("playground_undo").performClick()
        waitFor { editorText() == unformattedCode }
    }

    @Test
    fun findBar_opensFromTheToolbar() {
        clickTag("playground_find")
        assertTagDisplayed("playground_find_bar")
        replaceText("playground_find_query", "Greeter")
        waitForTag("playground_find_count")
    }

    @Test
    fun clearEditor_showsTheEmptyState() {
        openMenuEntry("playground_clear")
        assertTagDisplayed("playground_empty")
    }

    @Test
    fun saveAsSnippet_appearsInTheSnippetLibrary() {
        setEditor(validCode)
        openMenuEntry("playground_save_snippet")
        replaceText("snippet_title", "UiTest Broken snippet")
        clickTag("snippet_save")
        waitForTagGone("snippet_save")

        openMenuEntry("playground_library")
        waitForTag("snippet_list")
        waitFor { composeRule.onAllNodes(hasText("UiTest Broken snippet")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(hasText("UiTest Broken snippet")).assertIsDisplayed()
    }
}
