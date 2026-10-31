package com.riyadm.smalihelperplus.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompatibilityCheckerTest {

    private val opcodes = listOf(
        OpcodeCompat("const/4", 1, "035"),
        OpcodeCompat("const-string", 1, "035"),
        OpcodeCompat("invoke-virtual", 1, "035"),
        OpcodeCompat("invoke-direct", 1, "035"),
        OpcodeCompat("return-void", 1, "035"),
        OpcodeCompat("invoke-polymorphic", 26, "038"),
        OpcodeCompat("invoke-custom", 26, "038"),
        OpcodeCompat("const-method-handle", 28, "039"),
    )

    @Test fun extractsOnlyInstructionLines() {
        val code = """
            .class public Lcom/a/B;
            .super Ljava/lang/Object;
            # comment line
            .method public m()V
                .locals 1
                .line 3
                const/4 v0, 0x1   # trailing
                :cond_0
                invoke-virtual {p0}, Lcom/a/B;->x()V
                return-void
            .end method
        """.trimIndent()
        val ins = CompatibilityChecker.extractInstructions(code)
        assertThat(ins.map { it.mnemonic }).containsExactly("const/4", "invoke-virtual", "return-void").inOrder()
        assertThat(ins.map { it.line }).containsExactly(7, 9, 10).inOrder()
    }

    @Test fun skipsDataBlocks() {
        val code = """
            .method m()V
                packed-switch v0, :data
                :data
                .packed-switch 0x0
                    :a
                    :b
                .end packed-switch
                .array-data 4
                    0x1
                    0x2
                .end array-data
                .annotation runtime Lfoo/Bar;
                    value = "const-string"
                .end annotation
                return-void
            .end method
        """.trimIndent()
        val ins = CompatibilityChecker.extractInstructions(code)
        assertThat(ins.map { it.mnemonic }).containsExactly("packed-switch", "return-void").inOrder()
    }

    @Test fun flagsInstructionsAboveTargetApi() {
        val code = "invoke-polymorphic {v0}, Lx;->y()V\nconst/4 v0, 0x1\nconst-method-handle v1, x"
        val r = CompatibilityChecker.check(code, 24, opcodes)
        assertThat(r.issues.map { it.mnemonic }).containsExactly("invoke-polymorphic", "const-method-handle").inOrder()
        assertThat(r.issues[0].requiredApi).isEqualTo(26)
        assertThat(r.issues[1].line).isEqualTo(3)
        assertThat(r.requiredMinApi).isEqualTo(28)
        assertThat(r.requiredDexVersion).isEqualTo("039")
        assertThat(r.compatible).isFalse()
    }

    @Test fun compatibleWhenTargetIsHighEnough() {
        val code = "invoke-polymorphic {v0}, Lx;->y()V"
        val r = CompatibilityChecker.check(code, 26, opcodes)
        assertThat(r.compatible).isTrue()
        assertThat(r.requiredMinApi).isEqualTo(26)
        assertThat(r.requiredDexVersion).isEqualTo("038")
    }

    @Test fun reportsUnknownMnemonics() {
        val code = "const/4 v0, 0x1\nfoo-bar v0\nreturn-void"
        val r = CompatibilityChecker.check(code, 21, opcodes)
        assertThat(r.unknown.map { it.mnemonic }).containsExactly("foo-bar")
        assertThat(r.unknown.single().line).isEqualTo(2)
        assertThat(r.instructionCount).isEqualTo(3)
        assertThat(r.usage).containsExactly("const/4", 1, "return-void", 1)
    }

    @Test fun emptyCodeDefaults() {
        val r = CompatibilityChecker.check("", 21, opcodes)
        assertThat(r.instructionCount).isEqualTo(0)
        assertThat(r.requiredMinApi).isEqualTo(1)
        assertThat(r.requiredDexVersion).isEqualTo("035")
        assertThat(r.compatible).isTrue()
    }

    @Test fun newerThanListsSorted() {
        val list = CompatibilityChecker.newerThan(opcodes)
        assertThat(list.map { it.name }).containsExactly("invoke-custom", "invoke-polymorphic", "const-method-handle").inOrder()
    }

    @Test fun androidVersionLabels() {
        assertThat(AndroidVersions.label(26)).isEqualTo("API 26 – Android 8.0 Oreo")
        assertThat(AndroidVersions.all).hasSize(37)
        assertThat(AndroidVersions.apiForDexVersion("038")).isEqualTo(26)
        assertThat(AndroidVersions.apiForDexVersion("999")).isNull()
    }
}
