package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.smali.explain.InstructionExplainer
import com.riyadm.smalihelperplus.core.smali.explain.PseudocodeGenerator
import org.junit.Test

class SmaliExplainerTest {
    private fun explainSnippet(code: String): List<String> {
        val a = SmaliAnalyzer.analyzeSnippet(code)
        return InstructionExplainer.explainAll(a.file.methods.single()).map { it.explanation }
    }

    private fun pseudo(code: String, method: Int = 0): String {
        val a = SmaliAnalyzer.analyze(code)
        return PseudocodeGenerator.generate(a.file, a.file.methods[method])
    }

    @Test
    fun `const 4 example uses the actual register and value`() {
        assertThat(explainSnippet("const/4 v0, 0x1").single()).isEqualTo("Loads the integer value 1 into register v0.")
    }

    @Test
    fun `literal explanations sign-extend hex bit patterns`() {
        val e = explainSnippet("const/16 v1, 0xffff\nconst v2, 0x7f0a0001\nconst-wide/16 v3, 0x5")
        assertThat(e[0]).isEqualTo("Loads the integer value -1 (0xffff) into register v1.")
        assertThat(e[1]).contains("2131361793 (0x7f0a0001)")
        assertThat(e[2]).isEqualTo("Loads the long value 5 into the register pair v3/v4.")
    }

    @Test
    fun `high16 explains the float interpretation`() {
        val e = explainSnippet("const/high16 v0, 0x41200000").single()
        assertThat(e).contains("0x41200000")
        assertThat(e).contains("10.0")
    }

    @Test
    fun `explains moves, arithmetic and branches`() {
        val e = explainSnippet(
            """
            move-object v0, v1
            add-int v2, v0, v1
            add-int/2addr v2, v3
            rsub-int/lit8 v0, v1, 0xa
            if-eqz v0, :done
            :done
            """.trimIndent(),
        )
        assertThat(e[0]).isEqualTo("Copies the object reference in v1 into register v0.")
        assertThat(e[1]).isEqualTo("Computes v0 + v1 as int and stores the result in register v2.")
        assertThat(e[2]).contains("stores the result back in register v2")
        assertThat(e[3]).isEqualTo("Computes 10 - v1 (reverse subtraction) and stores the int result in v0.")
        assertThat(e[4]).contains("Jumps to :done if v0 is zero")
    }

    @Test
    fun `explains invokes with arguments and results`() {
        val code = classWith(
            """
            invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
            move-result-object v1
            invoke-static {v2, v3}, Ljava/lang/Math;->abs(J)J
            return-void
            """,
            signature = "test()V",
            flags = "public",
        )
        val a = SmaliAnalyzer.analyze(code)
        val steps = InstructionExplainer.explainAll(a.file.methods.single())
        assertThat(steps[0].explanation).contains("virtual method StringBuilder.append()")
        assertThat(steps[0].explanation).contains("p0 (this)")
        assertThat(steps[0].explanation).contains("v0 (int)")
        assertThat(steps[0].explanation).contains("move-result-object")
        assertThat(steps[1].explanation).isEqualTo("Stores the object result of the call to StringBuilder.append(int) in register v1.")
        assertThat(steps[2].explanation).contains("v2/v3 (long)")
    }

    @Test
    fun `unknown instructions are reported honestly`() {
        assertThat(explainSnippet("frobnicate v0").single()).contains("not a Dalvik instruction")
    }

    @Test
    fun `pseudocode carries the disclaimer and translates simple code`() {
        val p = pseudo(SmaliSamples.STARTER, method = 1)
        assertThat(p).startsWith("// ${PseudocodeGenerator.DISCLAIMER}")
        assertThat(p).contains("public String greet(String name) {")
        assertThat(p).contains("v0 = this.count;")
        assertThat(p).contains("v0 = v0 + 1;")
        assertThat(p).contains("this.count = v0;")
        assertThat(p).contains("if (name == null) {")
        assertThat(p).contains("name = \"stranger\";")
        assertThat(p).contains("v1 = new StringBuilder();")
        assertThat(p).contains("v1.append(name);")
        assertThat(p).contains("v1 = v1.toString();")
        assertThat(p).contains("return v1;")
        assertThat(p).doesNotContain("untranslated")
    }

    @Test
    fun `pseudocode structures simple if-then and keeps loops as goto`() {
        val max = pseudo(SmaliSamples.ARITHMETIC, 0)
        assertThat(max).contains("if (p0 >= p1) {")
        assertThat(max).contains("    return p0;")
        assertThat(max).doesNotContain("goto")
        val loop = pseudo(SmaliSamples.LOOP)
        assertThat(loop).contains("public static int sum(int[] values) {")
        assertThat(loop).contains("v2 = values.length;")
        assertThat(loop).contains("if (v1 >= v2) goto cond_0;")
        assertThat(loop).contains("v3 = values[v1];")
        assertThat(loop).contains("v0 += v3;")
        assertThat(loop).contains("goto goto_0;")
        assertThat(loop).contains("goto_0:")
    }

    @Test
    fun `pseudocode structures if-else`() {
        val code = classWith(
            """
            if-eqz p0, :cond_0
            const/4 v0, 0x1
            goto :goto_0
            :cond_0
            const/4 v0, 0x2
            :goto_0
            return v0
            """,
            signature = "pick(I)I",
            frame = ".registers 2",
        )
        val p = pseudo(code)
        assertThat(p).contains("if (p0 != 0) {\n        v0 = 1;\n    } else {\n        v0 = 2;\n    }\n    return v0;")
    }

    @Test
    fun `pseudocode handles switch, try-catch, arrays and untranslated instructions`() {
        val sw = pseudo(SmaliSamples.SWITCH)
        assertThat(sw).contains("switch (p0) {")
        assertThat(sw).contains("case 1: goto pswitch_0;")
        assertThat(sw).contains("case 2: goto pswitch_1;")
        val tc = pseudo(SmaliSamples.TRY_CATCH)
        assertThat(tc).contains("v0 = Integer.parseInt(p0);")
        assertThat(tc).contains("v1 = caughtException;  // NumberFormatException")
        assertThat(tc).contains("try range :try_start_0 .. :try_end_0")
        assertThat(tc).contains("v0 = -1;")
        val arrays = pseudo(
            classWith(
                """
                new-array v0, v1, [I
                filled-new-array {v1, v2}, [I
                move-result-object v3
                aput v1, v0, v2
                monitor-enter v0
                return-void
                """,
            ),
        )
        assertThat(arrays).contains("v0 = new int[v1];")
        assertThat(arrays).contains("v3 = new int[] {v1, v2};")
        assertThat(arrays).contains("v0[v2] = v1;")
        assertThat(arrays).contains("/* untranslated: monitor-enter v0 */")
    }

    @Test
    fun `constructors call super and new-instance merges with init`() {
        val p = pseudo(SmaliSamples.STARTER, 0)
        assertThat(p).contains("public Greeter() {")
        assertThat(p).contains("super();")
    }
}
