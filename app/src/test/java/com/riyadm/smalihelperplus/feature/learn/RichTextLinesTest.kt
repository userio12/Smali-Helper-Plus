package com.riyadm.smalihelperplus.feature.learn

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichTextLinesTest {
    @Test
    fun singleLineBreakInsideAParagraphIsASoftWrap() {
        assertThat(joinSoftBreaks("Smali is a text syntax for it:\nevery instruction has a textual form."))
            .containsExactly("Smali is a text syntax for it: every instruction has a textual form.")
    }

    @Test
    fun listItemsStayOnTheirOwnLines() {
        assertThat(joinSoftBreaks("Steps:\n- first\n- second\n1. one\n2) two"))
            .containsExactly("Steps:", "- first", "- second", "1. one", "2) two").inOrder()
    }

    @Test
    fun continuationLineJoinsThePreviousListItem() {
        assertThat(joinSoftBreaks("- a long bullet\n  that wraps\n- next"))
            .containsExactly("- a long bullet that wraps", "- next").inOrder()
    }

    @Test
    fun blankLinesAndIndentationAreDropped() {
        assertThat(joinSoftBreaks("  \n  only line  \n")).containsExactly("only line")
        assertThat(joinSoftBreaks("")).isEmpty()
    }
}
