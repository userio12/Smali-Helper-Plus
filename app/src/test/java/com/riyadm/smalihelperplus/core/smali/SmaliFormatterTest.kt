package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.riyadm.smalihelperplus.core.smali.format.SmaliFormatter
import com.riyadm.smalihelperplus.core.smali.lexer.SmaliLexer
import com.riyadm.smalihelperplus.core.smali.lexer.TokenType
import org.junit.Test

class SmaliFormatterTest {
    private val messy = """
        .class   public Lcom/acme/demo/M;
        .super Ljava/lang/Object;
        .field private x:I
        .field private static final NAME:Ljava/lang/String; = "a  b"
        .method public static run(I)V
        .registers 4
                const/4 v0,0x1     # keep   this comment
          const-string v1 ,  "two  spaces, and # hash"
        invoke-static { v0 , v1 }, Lcom/acme/demo/M;->f(ILjava/lang/String;)V
           invoke-static/range {v0..v1}, Lcom/acme/demo/M;->f(ILjava/lang/String;)V
        packed-switch p0, :data
        return-void
        :a
        return-void
        :data
        .packed-switch 0x0
        :a
        .end packed-switch
        .end method
        .method public static g()V
        .registers 1
        .annotation system Ldalvik/annotation/Throws;
        value = {
        Ljava/io/IOException;
        }
        .end annotation
        return-void
        .end method
    """.trimIndent()

    @Test
    fun `formats indentation and spacing canonically`() {
        val result = SmaliFormatter.format(messy)
        assertThat(result.refused).isFalse()
        val lines = result.text.lines()
        assertThat(lines).contains(".class public Lcom/acme/demo/M;")
        assertThat(lines).contains("    const/4 v0, 0x1 # keep   this comment")
        assertThat(lines).contains("    const-string v1, \"two  spaces, and # hash\"")
        assertThat(lines).contains("    invoke-static {v0, v1}, Lcom/acme/demo/M;->f(ILjava/lang/String;)V")
        assertThat(lines).contains("    invoke-static/range {v0 .. v1}, Lcom/acme/demo/M;->f(ILjava/lang/String;)V")
        assertThat(lines).contains("    .packed-switch 0x0")
        assertThat(lines).contains("        :a")
        assertThat(lines).contains("    .end packed-switch")
        assertThat(lines).contains("        value = {")
        assertThat(lines).contains("            Ljava/io/IOException;")
        assertThat(lines).contains("        }")
        assertThat(lines).contains("    .end annotation")
        assertThat(lines.none { it.endsWith(" ") }).isTrue()
    }

    @Test
    fun `members are separated by blank lines`() {
        val lines = SmaliFormatter.format(messy).text.lines()
        val methodIdx = lines.indexOf(".method public static g()V")
        assertThat(lines[methodIdx - 1]).isEmpty()
        val fieldIdx = lines.indexOfFirst { it.startsWith(".field private static final NAME") }
        assertThat(lines[fieldIdx - 1]).isEmpty()
    }

    @Test
    fun `formatting is idempotent`() {
        val once = SmaliFormatter.format(messy).text
        val twice = SmaliFormatter.format(once).text
        assertThat(twice).isEqualTo(once)
        SmaliSamples.all.forEach { s ->
            val a = SmaliFormatter.format(s.code).text
            assertWithMessage(s.id).that(SmaliFormatter.format(a).text).isEqualTo(a)
        }
    }

    @Test
    fun `formatting preserves every token`() {
        fun tokens(t: String) = SmaliLexer.tokenize(t).map { it.type to it.text }
        val formatted = SmaliFormatter.format(messy).text
        assertThat(tokens(formatted)).containsExactlyElementsIn(tokens(messy)).inOrder()
        assertThat(SmaliAnalyzer.analyze(formatted).diagnostics.map { it.code })
            .containsExactlyElementsIn(SmaliAnalyzer.analyze(messy).diagnostics.map { it.code })
    }

    @Test
    fun `built-in samples are already canonical`() {
        SmaliSamples.all.forEach { s ->
            assertWithMessage(s.id).that(SmaliFormatter.format(s.code).text).isEqualTo(s.code)
        }
    }

    @Test
    fun `refuses unterminated literals and unbalanced blocks`() {
        val bad = ".method public a()V\n  const-string v0, \"open\n.end method\n"
        val r = SmaliFormatter.format(bad)
        assertThat(r.refused).isTrue()
        assertThat(r.text).isEqualTo(bad)
        assertThat(r.diagnostics).isNotEmpty()
        val unbalanced = ".method public a()V\n  return-void\n"
        assertThat(SmaliFormatter.format(unbalanced).refused).isTrue()
        val stray = "return-void\n.end annotation\n"
        assertThat(SmaliFormatter.format(stray).refused).isTrue()
    }

    @Test
    fun `comment-only lines and blank runs are normalised`() {
        val text = ".class public LA;\n\n\n\n.super Ljava/lang/Object;\n# trailing   comment   \n"
        val out = SmaliFormatter.format(text).text
        assertThat(out).isEqualTo(".class public LA;\n\n.super Ljava/lang/Object;\n# trailing   comment   \n")
        assertThat(SmaliLexer.tokenize(out).last().type).isEqualTo(TokenType.COMMENT)
    }
}
