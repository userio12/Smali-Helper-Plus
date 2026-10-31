package com.riyadm.smalihelperplus.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmaliDiffTest {

    @Test fun identicalInputs() {
        val r = SmaliDiff.diff("a\nb\nc", "a\nb\nc")
        assertThat(r.identical).isTrue()
        assertThat(r.stats).isEqualTo(DiffStats(0, 0, 0, 3))
        assertThat(r.unified.all { it.kind == DiffKind.EQUAL }).isTrue()
        assertThat(r.toPatchText()).isEqualTo("No differences")
    }

    @Test fun emptyInputs() {
        assertThat(SmaliDiff.diff("", "").identical).isTrue()
        val onlyRight = SmaliDiff.diff("", "x\ny")
        assertThat(onlyRight.stats).isEqualTo(DiffStats(2, 0, 0, 0))
        val onlyLeft = SmaliDiff.diff("x\ny", "")
        assertThat(onlyLeft.stats).isEqualTo(DiffStats(0, 2, 0, 0))
    }

    @Test fun pureAddition() {
        val r = SmaliDiff.diff("a\nc", "a\nb\nc")
        assertThat(r.stats).isEqualTo(DiffStats(1, 0, 0, 2))
        val added = r.unified.single { it.kind == DiffKind.ADDED }
        assertThat(added.text).isEqualTo("b")
        assertThat(added.rightLine).isEqualTo(2)
        assertThat(added.leftLine).isNull()
    }

    @Test fun pureRemoval() {
        val r = SmaliDiff.diff("a\nb\nc", "a\nc")
        assertThat(r.stats).isEqualTo(DiffStats(0, 1, 0, 2))
        val removed = r.sideBySide.single { it.kind == DiffKind.REMOVED }
        assertThat(removed.left!!.text).isEqualTo("b")
        assertThat(removed.right).isNull()
    }

    @Test fun changedLinesPairUp() {
        val r = SmaliDiff.diff("const/4 v0, 0x1\nreturn v0", "const/4 v0, 0x2\nreturn v0")
        assertThat(r.stats).isEqualTo(DiffStats(0, 0, 1, 1))
        val row = r.sideBySide.single { it.kind == DiffKind.CHANGED }
        assertThat(row.left!!.text).isEqualTo("const/4 v0, 0x1")
        assertThat(row.right!!.text).isEqualTo("const/4 v0, 0x2")
        // unified shows removed then added
        val kinds = r.unified.map { it.kind }
        assertThat(kinds).containsExactly(DiffKind.REMOVED, DiffKind.ADDED, DiffKind.EQUAL).inOrder()
    }

    @Test fun intraLineHighlightsChangedTokens() {
        val old = "const/4 v0, 0x1"
        val new = "const/4 v0, 0x2"
        val (l, r) = SmaliDiff.intraLine(old, new)
        assertThat(l).hasSize(1)
        assertThat(old.substring(l[0].start, l[0].end)).isEqualTo("0x1")
        assertThat(new.substring(r[0].start, r[0].end)).isEqualTo("0x2")
    }

    @Test fun intraLineSkipsVeryDifferentLines() {
        val (l, r) = SmaliDiff.intraLine("alpha beta gamma", "one two three four")
        assertThat(l).isEmpty()
        assertThat(r).isEmpty()
    }

    @Test fun changedBlockWithUnequalSizes() {
        val r = SmaliDiff.diff("x\nold1\nold2\ny", "x\nnew1\ny")
        assertThat(r.stats.changed).isEqualTo(1)
        assertThat(r.stats.removed).isEqualTo(1)
        assertThat(r.stats.added).isEqualTo(0)
        assertThat(r.stats.unchanged).isEqualTo(2)
    }

    @Test fun ignoreWhitespace() {
        val a = "    const/4 v0, 0x1\n\n    return v0"
        val b = "const/4   v0,  0x1\nreturn v0"
        assertThat(SmaliDiff.diff(a, b).identical).isFalse()
        assertThat(SmaliDiff.diff(a, b, DiffOptions(ignoreWhitespace = true)).identical).isTrue()
    }

    @Test fun ignoreComments() {
        val a = "const/4 v0, 0x1 # one\n# only a comment\nreturn v0"
        val b = "const/4 v0, 0x1 # uno\nreturn v0"
        assertThat(SmaliDiff.diff(a, b).identical).isFalse()
        assertThat(SmaliDiff.diff(a, b, DiffOptions(ignoreComments = true)).identical).isTrue()
    }

    @Test fun commentMarkerInsideStringIsNotAComment() {
        val a = "const-string v0, \"a # b\""
        val b = "const-string v0, \"a # c\""
        assertThat(SmaliDiff.diff(a, b, DiffOptions(ignoreComments = true)).identical).isFalse()
        assertThat(SmaliDiff.stripComment("const-string v0, \"x # y\" # real")).isEqualTo("const-string v0, \"x # y\"")
    }

    @Test fun ignoreLineDirectives() {
        val a = ".line 10\nconst/4 v0, 0x1\n.line 11\nreturn v0"
        val b = ".line 20\nconst/4 v0, 0x1\nreturn v0"
        assertThat(SmaliDiff.diff(a, b).identical).isFalse()
        assertThat(SmaliDiff.diff(a, b, DiffOptions(ignoreLineDirectives = true)).identical).isTrue()
    }

    @Test fun lineNumbersStayOriginalWhenLinesAreIgnored() {
        val a = ".line 1\nfoo\nbar"
        val b = ".line 1\nfoo\nbaz"
        val r = SmaliDiff.diff(a, b, DiffOptions(ignoreLineDirectives = true))
        val row = r.sideBySide.single { it.kind == DiffKind.CHANGED }
        assertThat(row.left!!.lineNumber).isEqualTo(3)
        assertThat(row.right!!.lineNumber).isEqualTo(3)
        assertThat(r.sideBySide.first().left!!.lineNumber).isEqualTo(2)
    }

    @Test fun handlesWindowsLineEndingsAndTrailingNewline() {
        val r = SmaliDiff.diff("a\r\nb\r\n", "a\nb\n")
        assertThat(r.identical).isTrue()
        assertThat(r.stats.unchanged).isEqualTo(2)
    }

    @Test fun movedBlockIsRemovedAndAdded() {
        val r = SmaliDiff.diff("a\nb\nc\nd", "c\nd\na\nb")
        assertThat(r.stats.unchanged).isEqualTo(2)
        assertThat(r.stats.total).isEqualTo(4)
    }

    @Test fun minimalDiffOnInterleavedChanges() {
        val a = (1..10).joinToString("\n") { "line$it" }
        val b = (1..10).joinToString("\n") { if (it % 3 == 0) "changed$it" else "line$it" }
        val r = SmaliDiff.diff(a, b)
        assertThat(r.stats.changed).isEqualTo(3)
        assertThat(r.stats.unchanged).isEqualTo(7)
        assertThat(r.stats.added).isEqualTo(0)
        assertThat(r.stats.removed).isEqualTo(0)
    }

    @Test fun diffReconstructsBothSides() {
        val a = "p\nq\nr\ns\nt\nu"
        val b = "p\nr\nx\ns\nt\ny\nz"
        val r = SmaliDiff.diff(a, b)
        val left = r.unified.filter { it.kind != DiffKind.ADDED }.joinToString("\n") { it.text }
        val right = r.unified.filter { it.kind != DiffKind.REMOVED }.joinToString("\n") { it.text }
        assertThat(left).isEqualTo(a)
        assertThat(right).isEqualTo(b)
    }

    @Test fun largeSimilarInputsAreExact() {
        val a = (1..3000).joinToString("\n") { "i$it" }
        val b = a.replace("i1500", "changed").replace("i2999", "other")
        val r = SmaliDiff.diff(a, b)
        assertThat(r.approximate).isFalse()
        assertThat(r.stats.changed).isEqualTo(2)
    }

    @Test fun totallyDifferentLargeInputsFallBackToApproximate() {
        val a = (1..3000).joinToString("\n") { "a$it" }
        val b = (1..3000).joinToString("\n") { "b$it" }
        val r = SmaliDiff.diff(a, b)
        assertThat(r.approximate).isTrue()
        assertThat(r.stats.changed).isEqualTo(3000)
    }

    @Test fun patchTextHasHunks() {
        val a = (1..10).joinToString("\n") { "l$it" }
        val b = a.replace("l5", "L5")
        val patch = SmaliDiff.diff(a, b).toPatchText("old.smali", "new.smali", context = 1)
        assertThat(patch).contains("--- old.smali")
        assertThat(patch).contains("+++ new.smali")
        assertThat(patch).contains("@@ -4,3 +4,3 @@")
        assertThat(patch).contains("-l5")
        assertThat(patch).contains("+L5")
    }
}
