package com.riyadm.smalihelperplus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class ToolsTest : BaseUiTest() {

    private fun openTool(tag: String) {
        openTab("nav_tools")
        waitForTag("tools_grid")
        scrollToAndClick("tools_grid", "tool_$tag")
    }

    @Test
    fun toolsGrid_listsTheConverters() {
        openTab("nav_tools")
        assertTagDisplayed("tools_grid")
        scrollTo("tools_grid", "tool_register_calculator")
        scrollTo("tools_grid", "tool_number_converter")
        scrollTo("tools_grid", "tool_descriptor_converter")
        scrollTo("tools_grid", "tool_snippet_library")
    }

    @Test
    fun toolsSearch_filtersTheGrid() {
        openTab("nav_tools")
        replaceText("tools_search", "hex")
        waitForTag("tool_number_converter")
        waitForTagGone("tool_register_calculator")
    }

    @Test
    fun registerCalculator_instanceMethodWithWidePair_needsSixRegisters() {
        openTool("register_calculator")
        replaceText("regcalc_descriptor", "(IJ)V")
        clickTag("regcalc_instance")
        replaceText("regcalc_locals", "2")

        // 2 locals + this + int + long (2 words) = 6 registers.
        waitForText(".registers 6")
        waitForText(".locals 2")
    }

    @Test
    fun registerCalculator_staticMethodDropsThis() {
        openTool("register_calculator")
        replaceText("regcalc_descriptor", "(IJ)V")
        clickTag("regcalc_static")
        replaceText("regcalc_locals", "2")

        // 2 locals + int + long (2 words) = 5 registers.
        waitForText(".registers 5")
    }

    @Test
    fun numberConverter_showsHexFor255() {
        openTool("number_converter")
        replaceText("numconv_input", "255")
        // 32-bit hex view and the shortest smali literal.
        waitForText("0x000000ff")
        waitForText("0xff")
    }

    @Test
    fun numberConverter_rejectsGarbage() {
        openTool("number_converter")
        replaceText("numconv_input", "12zz")
        // The intro hint is replaced by an error card; no result rows are produced.
        waitFor { !textExists("Type a number", substring = true) }
        assertTrue(!textExists("Signed decimal"))
        composeRule.onNodeWithTag("numconv_input").assertIsDisplayed()
    }

    @Test
    fun descriptorConverter_javaTypeBecomesDalvikDescriptor() {
        openTool("descriptor_converter")
        replaceText("desc_input", "java.lang.String")
        waitForText("Ljava/lang/String;")
    }

    @Test
    fun descriptorConverter_descriptorBecomesJavaType() {
        openTool("descriptor_converter")
        replaceText("desc_input", "[Ljava/lang/String;")
        waitForText("java.lang.String[]")
    }

    @Test
    fun toolScreen_backReturnsToGrid() {
        openTool("descriptor_converter")
        waitForTag("desc_input")
        pressSystemBack()
        waitForTag("tools_grid")
    }
}
