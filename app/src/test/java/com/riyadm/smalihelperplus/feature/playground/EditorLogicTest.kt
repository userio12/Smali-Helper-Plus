package com.riyadm.smalihelperplus.feature.playground

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.feature.playground.editor.EditHistory
import com.riyadm.smalihelperplus.feature.playground.editor.EditorLogic
import com.riyadm.smalihelperplus.feature.playground.editor.EditorText
import org.junit.Test

class EditorLogicTest {
    private fun typeEnter(text: String, cursor: Int = text.length): EditorText? {
        val old = EditorText(text, cursor)
        val new = EditorText(text.substring(0, cursor) + "\n" + text.substring(cursor), cursor + 1)
        return EditorLogic.autoIndent(old, new)
    }

    @Test
    fun `enter keeps indentation and indents after block openers`() {
        val afterMethod = typeEnter(".method public static a()V")!!
        assertThat(afterMethod.text).isEqualTo(".method public static a()V\n    ")
        assertThat(afterMethod.cursor).isEqualTo(afterMethod.text.length)
        val body = typeEnter(".method a()V\n    const/4 v0, 0x1")!!
        assertThat(body.text.substringAfterLast('\n')).isEqualTo("    ")
        val payload = typeEnter(".method a()V\n    .packed-switch 0x1")!!
        assertThat(payload.text.substringAfterLast('\n')).isEqualTo("        ")
    }

    @Test
    fun `enter after an end directive dedents it to its opener`() {
        val r = typeEnter(".method a()V\n    return-void\n    .end method")!!
        assertThat(r.text).isEqualTo(".method a()V\n    return-void\n.end method\n")
        assertThat(r.cursor).isEqualTo(r.text.length)
        val nested = typeEnter(".method a()V\n    .packed-switch 0x0\n        :a\n        .end packed-switch")!!
        assertThat(nested.text.lines()[3]).isEqualTo("    .end packed-switch")
        assertThat(nested.text.substringAfterLast('\n')).isEqualTo("    ")
    }

    @Test
    fun `non newline edits are not auto-indented`() {
        val old = EditorText("abc", 3)
        assertThat(EditorLogic.autoIndent(old, EditorText("abcd", 4))).isNull()
        assertThat(EditorLogic.autoIndent(old, EditorText("ab", 2))).isNull()
    }

    @Test
    fun `find and replace all`() {
        val text = "const/4 v0, 0x1\nCONST/4 v1, 0x2"
        assertThat(EditorLogic.findMatches(text, "const/4", matchCase = true)).hasSize(1)
        assertThat(EditorLogic.findMatches(text, "const/4", matchCase = false)).containsExactly(0..6, 16..22).inOrder()
        val (out, n) = EditorLogic.replaceAll(text, "v", "p", matchCase = true)
        assertThat(n).isEqualTo(2)
        assertThat(out).isEqualTo("const/4 p0, 0x1\nCONST/4 p1, 0x2")
        assertThat(EditorLogic.findMatches(text, "", matchCase = true)).isEmpty()
    }

    @Test
    fun `register occurrences are whole words outside strings and comments`() {
        val text = "move v1, v10\nconst-string v0, \"v1\" # v1\nreturn v1"
        val occ = EditorLogic.registerOccurrences(text, "v1")
        assertThat(occ.map { text.substring(it.first, it.last + 1) }).containsExactly("v1", "v1")
        assertThat(occ.first().first).isEqualTo(5)
        assertThat(EditorLogic.registerAt(text, 6)).isEqualTo("v1")
        assertThat(EditorLogic.registerAt(text, 1)).isNull()
    }

    @Test
    fun `matching brackets skip strings`() {
        val text = "invoke-static {v0, v1}, LA;->b(II)V"
        assertThat(EditorLogic.matchingBracket(text, 14)).isEqualTo(14 to 21)
        assertThat(EditorLogic.matchingBracket(text, 22)).isEqualTo(21 to 14)
        assertThat(EditorLogic.matchingBracket(text, text.indexOf('(') + 1)).isEqualTo(text.indexOf('(') to text.indexOf(')'))
        val s = "const-string v0, \"{\""
        assertThat(EditorLogic.matchingBracket(s, s.indexOf('{') + 1)).isNull()
    }

    @Test
    fun `completions come from the opcode table and directives`() {
        val text = "    invoke-st"
        val c = EditorLogic.completionAt(text, text.length)!!
        assertThat(c.suggestions).containsAtLeast("invoke-static", "invoke-static/range")
        val applied = EditorLogic.applyCompletion(text, c, "invoke-static")
        assertThat(applied.text).isEqualTo("    invoke-static ")
        assertThat(applied.cursor).isEqualTo(applied.text.length)
        val d = EditorLogic.completionAt(".loc", 4)!!
        assertThat(d.suggestions).containsAtLeast(".locals", ".local")
        assertThat(EditorLogic.completionAt("const/4 v", 9)).isNull() // not the first word of the line
    }

    @Test
    fun `line helpers`() {
        val starts = EditorLogic.lineStarts("a\nbc\n\nd")
        assertThat(starts.toList()).containsExactly(0, 2, 5, 6).inOrder()
        assertThat(EditorLogic.lineOf(starts, 0)).isEqualTo(0)
        assertThat(EditorLogic.lineOf(starts, 3)).isEqualTo(1)
        assertThat(EditorLogic.lineOf(starts, 5)).isEqualTo(2)
        assertThat(EditorLogic.lineOf(starts, 7)).isEqualTo(3)
    }

    @Test
    fun `history coalesces typing and is bounded`() {
        val h = EditHistory(maxEntries = 3)
        var cur = EditorText("", 0)
        "abc".forEachIndexed { i, ch ->
            val next = EditorText(cur.text + ch, cur.cursor + 1)
            h.record(cur, next, nowMillis = 1000L + i * 100)
            cur = next
        }
        // One undo step restores the empty buffer because the burst was coalesced.
        val undone = h.undo(cur)!!
        assertThat(undone.text).isEmpty()
        assertThat(h.redo(undone)!!.text).isEqualTo("abc")

        val bounded = EditHistory(maxEntries = 3)
        var state = EditorText("", 0)
        repeat(6) { i ->
            val next = EditorText(state.text + "line$i\n", 0)
            bounded.record(state, next, nowMillis = i * 10_000L, force = true)
            state = next
        }
        var steps = 0
        while (bounded.canUndo) { state = bounded.undo(state)!!; steps++ }
        assertThat(steps).isEqualTo(3)
        val disabled = EditHistory(maxEntries = -1)
        disabled.record(EditorText("", 0), EditorText("a", 1), 0)
        assertThat(disabled.canUndo).isFalse()
    }

    @Test
    fun `new edit clears redo stack`() {
        val h = EditHistory()
        val a = EditorText("a", 1)
        val b = EditorText("ab", 2)
        h.record(a, b, 0, force = true)
        val back = h.undo(b)!!
        assertThat(h.canRedo).isTrue()
        h.record(back, EditorText("ax", 2), 5_000, force = true)
        assertThat(h.canRedo).isFalse()
    }
    @Test fun `register highlighting rejects empty names respects limits and identifier boundaries`() {
        assertThat(EditorLogic.registerOccurrences("move v0, v0", "")).isEmpty()
        assertThat(EditorLogic.registerOccurrences("v0 v0 v0", "v0", 1)).hasSize(1)
        assertThat(EditorLogic.registerOccurrences("v0_name v0.name v0", "v0")).containsExactly(16..17)
    }

    @Test fun `undo separates cursor jumps forced edits and edits after redo`() {
        val h = EditHistory()
        val a = EditorText("abc", 3)
        val b = EditorText("abcd", 4)
        h.record(a, b, 10)
        val jumped = b.copy(selectionStart = 0, selectionEnd = 0)
        val c = EditorText("Xabcd", 1)
        h.record(jumped, c, 20)
        assertThat(h.undo(c)).isEqualTo(jumped)
        assertThat(h.redo(jumped)).isEqualTo(c)
        val d = EditorText("XYabcd", 2)
        h.record(c, d, 30)
        assertThat(h.undo(d)).isEqualTo(c)
    }

    @Test fun `auto indent handles reversed selection`() {
        val old = EditorText("    abc", 7, 4)
        val result = EditorLogic.autoIndent(old, EditorText("    \n", 5))!!
        assertThat(result.text).isEqualTo("    \n    ")
    }

    @Test(timeout = 2000) fun `bracket matching is linear and ignores char literals with CRLF`() {
        val text = "(" + "'(' ".repeat(5000) + ")"
        assertThat(EditorLogic.matchingBracket(text, 0, text.length)).isEqualTo(0 to text.lastIndex)
        assertThat(EditorLogic.matchingBracket("# x\r\n'(' ()", 6)).isNull()
    }

    @Test fun `replace all treats regex syntax literally and consumes nonoverlapping matches`() {
        assertThat(EditorLogic.replaceAll("aaaaa", "aa", "$", true)).isEqualTo("\$\$a" to 2)
        assertThat(EditorLogic.replaceAll(".* .*", ".*", "$1", true)).isEqualTo("$1 $1" to 2)
    }

    @Test fun `indent ignores braces inside comments and preserves end local indentation`() {
        assertThat(typeEnter("    nop # {")!!.text).isEqualTo("    nop # {\n    ")
        assertThat(typeEnter(".method a()V\n    .end local v0")!!.text).endsWith("    .end local v0\n    ")
        assertThat(typeEnter(".method\tpublic a()V")!!.text).endsWith("\n    ")
    }

}
