package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.smali.ast.FieldDefinition
import com.riyadm.smalihelperplus.core.smali.ast.ParamDirective
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.format.SmaliFormatter
import com.riyadm.smalihelperplus.core.smali.parser.SmaliParser
import org.junit.Test
import kotlin.random.Random

class SmaliRobustnessTest {
    /** Runs every engine stage; any exception fails the test. */
    private fun exercise(text: String) {
        val a = SmaliAnalyzer.analyze(text)
        val s = SmaliAnalyzer.analyzeSnippet(text)
        a.outline
        listOf(a, s).forEach { analysis ->
            analysis.file.methods.forEach { SmaliAnalyzer.insight(analysis.file, it) }
        }
        val f = SmaliFormatter.format(text)
        if (!f.refused) assertThat(SmaliFormatter.format(f.text).text).isEqualTo(f.text)
    }

    @Test
    fun `engine never throws on truncated or mutated input`() {
        val random = Random(42)
        val alphabet = " \n\t{}(),.:;\"'#-/>=0123456789vpLIJV[abcdefxyz\r\u263a\u0000"
        SmaliSamples.all.forEach { sample ->
            val code = sample.code
            // Every prefix (simulates typing).
            for (end in code.indices step 7) exercise(code.substring(0, end))
            // Random mutations.
            repeat(150) {
                val sb = StringBuilder(code)
                repeat(1 + random.nextInt(6)) {
                    val pos = random.nextInt(sb.length + 1)
                    when (random.nextInt(3)) {
                        0 -> sb.insert(pos, alphabet[random.nextInt(alphabet.length)])
                        1 -> if (pos < sb.length) sb.deleteCharAt(pos)
                        else -> if (pos < sb.length) sb.setCharAt(pos, alphabet[random.nextInt(alphabet.length)])
                    }
                }
                exercise(sb.toString())
            }
        }
    }

    @Test
    fun `odd operand shapes do not crash explanations`() {
        val code = classWith(
            """
            const/4 {v0}, :x
            invoke-static v0, v1
            packed-switch v0, :nowhere
            fill-array-data v0, 0x1
            invoke-static/range {p9 .. v0}, LA;->b()V
            move-result
            return-void
            """,
        )
        exercise(code)
        assertThat(SmaliAnalyzer.analyze(code).errorCount).isGreaterThan(3)
    }

    @Test
    fun `field and param annotation blocks are parsed and formatted`() {
        val code = """
            .class public LA;
            .super Ljava/lang/Object;

            .field public name:Ljava/lang/String;
                .annotation runtime Ljava/lang/Deprecated;
                .end annotation
            .end field

            .method public a(Ljava/lang/String;)V
                .registers 2
                .param p1, "s"
                    .annotation build Landroidx/annotation/Nullable;
                    .end annotation
                .end param

                return-void
            .end method
        """.trimIndent() + "\n"
        val parsed = SmaliParser.parse(code)
        assertThat(parsed.diagnostics).isEmpty()
        val field: FieldDefinition = parsed.file.fields.single()
        assertThat(field.annotations).hasSize(1)
        assertThat(field.endLine).isEqualTo(7)
        val param = parsed.file.methods.single().items.filterIsInstance<ParamDirective>().single()
        assertThat(param.name).isEqualTo("s")
        assertThat(param.endLine).isEqualTo(14)
        assertThat(SmaliFormatter.format(code).text).isEqualTo(code)
        assertThat(SmaliAnalyzer.analyze(code).diagnostics.map { it.code }).doesNotContain(DiagnosticCode.MISSING_END)
    }

    @Test
    fun `snippet with registers directive checks the frame`() {
        val a = SmaliAnalyzer.analyzeSnippet(".registers 2\nconst/4 v0, 0x1\nconst/4 v5, 0x1\n")
        assertThat(a.diagnostics.map { it.code }).contains(DiagnosticCode.REGISTER_OUT_OF_FRAME)
    }
}
