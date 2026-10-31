package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.lexer.SmaliLexer
import com.riyadm.smalihelperplus.core.smali.parser.SmaliParser
import com.riyadm.smalihelperplus.core.smali.ast.SourceRange
import com.riyadm.smalihelperplus.core.smali.format.SmaliFormatter
import org.junit.Test

class ReviewRegressionTest {
    @Test fun `typed integer bit patterns are interpreted before instruction narrowing`() {
        listOf("const/4 v0, 0xffffffff", "const/4 v0, 0xfft", "const/4 v0, '\\uffff'", "const/16 v0, 65535",
            "const-wide/16 v0, 0xffffffff", "const-wide/32 v0, 0xffffffffL",
            "add-int/lit8 v0, v1, 0xfft").forEach {
            assertThat(diagnostics(classWith("$it\nreturn-void")).errors()).isEmpty()
        }
        assertThat(SmaliParser.parseLiteral("0xfft", SourceRange(1, 0, 5))!!.value!!.toInt()).isEqualTo(-1)
        assertThat(SmaliParser.parseLiteral("256t", SourceRange(1, 0, 4))).isNull()
        assertThat(diagnostics(classWith("const/4 v0, 8\nreturn-void")).codes()).contains(DiagnosticCode.LITERAL_RANGE)
    }

    @Test fun `frame total includes parameter words in the ushort limit`() {
        assertThat(diagnostics(classWith("return-void", signature = "test(J)V", frame = ".locals 65535")).codes())
            .contains(DiagnosticCode.REGISTER_OUT_OF_FRAME)
    }

    @Test fun `branches cannot target payloads or end of method`() {
        listOf("goto :end\n:end", "goto :data\n:data\n.array-data 1\n0\n.end array-data").forEach {
            assertThat(diagnostics(classWith(it)).codes()).contains(DiagnosticCode.OPERAND_KIND)
        }
    }

    @Test fun `catch ranges must be nonempty forward and have executable handlers`() {
        val body = ":a\nnop\n:b\nreturn-void\n:h\nmove-exception v0\nreturn-void\n"
        listOf("{:b .. :a} :h", "{:a .. :a} :h", "{:a .. :b} :end").forEach {
            assertThat(diagnostics(classWith(body + ".catchall $it\n:end")).codes()).contains(DiagnosticCode.RANGE_SYNTAX)
        }
    }

    @Test fun `polymorphic invokes require consecutive wide argument words`() {
        val code = "invoke-polymorphic {v0, v1, v3}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (J)V\nreturn-void"
        assertThat(diagnostics(classWith(code)).codes()).contains(DiagnosticCode.ARGUMENT_COUNT)
    }

    @Test fun `CRLF token offsets refer to original input`() {
        val text = "# hello\r\nmove v0, v1\r\nreturn-void\r\n"
        SmaliLexer.tokenize(text).forEach { token ->
            assertThat(text.substring(token.offset, token.endOffset)).isEqualTo(token.text)
        }
    }

    @Test fun `formatter preserves token text on CRLF tabs annotations and payloads`() {
        val text = classWith("const-string v0, \"a # { \\\" b\" # keep\nreturn-void").replace("    ", "\t").replace("\n", "\r\n")
        val formatted = SmaliFormatter.format(text)
        assertThat(formatted.refused).isFalse()
        assertThat(SmaliLexer.tokenize(formatted.text).map { it.text }).isEqualTo(SmaliLexer.tokenize(text).map { it.text })
        assertThat(SmaliFormatter.format(formatted.text).text).isEqualTo(formatted.text)
    }
    @Test fun `subannotations in assigned values preserve structure and formatting`() {
        val text = ".class public LA;\n.super Ljava/lang/Object;\n.annotation runtime LB;\nvalue = .subannotation LC;\nx = 1\n.end subannotation\n.end annotation\n"
        assertThat(SmaliParser.parse(text).diagnostics).isEmpty()
        val result = SmaliFormatter.format(text)
        assertThat(result.refused).isFalse()
        assertThat(SmaliFormatter.format(result.text).text).isEqualTo(result.text)
        assertThat(SmaliLexer.tokenize(result.text).map { it.text }).isEqualTo(SmaliLexer.tokenize(text).map { it.text })
        assertThat(SmaliParser.parse(text.replace(".end subannotation\n", "")).diagnostics.map { it.code }).contains(DiagnosticCode.MISSING_END)
    }

    @Test fun `invalid string escapes are diagnosed but valid unicode is accepted`() {
        assertThat(diagnostics(classWith("const-string v0, \"bad\\q\"\nreturn-void")).codes()).contains(DiagnosticCode.SYNTAX)
        assertThat(diagnostics(classWith("const-string v0, \"ok\\u263a\"\nreturn-void")).errors()).isEmpty()
    }

    @Test(timeout = 10000) fun `analysis handles a five thousand line method`() {
        val text = classWith("const/4 v0, 0\n" + "add-int/lit8 v0, v0, 1\n".repeat(4990) + "return-void")
        val analysis = SmaliAnalyzer.analyze(text)
        assertThat(analysis.errorCount).isEqualTo(0)
        analysis.file.methods.forEach { SmaliAnalyzer.insight(analysis.file, it) }
    }

    @Test fun `wide float constants preserve raw bits rather than convert to double`() {
        val a = SmaliAnalyzer.analyze(classWith("const-wide v0, 1.0f\nreturn-void"))
        val m = a.file.methods.single()
        assertThat(a.errorCount).isEqualTo(0)
        assertThat(com.riyadm.smalihelperplus.core.smali.explain.PseudocodeGenerator.generate(a.file, m)).contains("1065353216L /* raw float bits */")
        assertThat(com.riyadm.smalihelperplus.core.smali.explain.InstructionExplainer.explain(m.instructions.first(), m).explanation).contains("raw float bits")
        assertThat(diagnostics(classWith("const-wide/high16 v0, 1.0f\nreturn-void")).codes()).contains(DiagnosticCode.LITERAL_RANGE)
        assertThat(diagnostics(classWith("const/4 v0, true\nreturn-void")).codes()).contains(DiagnosticCode.OPERAND_KIND)
    }

    @Test fun `array payload accepts boolean raw constants and rejects malformed numbers`() {
        val body = "fill-array-data v0, :data\nreturn-void\n:data\n.array-data 1\n%s\n.end array-data"
        assertThat(diagnostics(classWith(body.format("true"))).errors()).isEmpty()
        assertThat(diagnostics(classWith(body.format("99999999999999999999999999999999"))).codes()).contains(DiagnosticCode.SYNTAX)
    }

    @Test fun `switch cases cannot jump into a payload`() {
        val code = "packed-switch v0, :data\nreturn-void\n:data\n.packed-switch 0\n:data\n.end packed-switch"
        assertThat(diagnostics(classWith(code)).codes()).contains(DiagnosticCode.OPERAND_KIND)
    }

    @Test fun `formatter preserves trailing whitespace belonging to comment tokens`() {
        val text = "nop # comment   \n"
        assertThat(SmaliLexer.tokenize(SmaliFormatter.format(text).text).map { it.text })
            .isEqualTo(SmaliLexer.tokenize(text).map { it.text })
    }

    @Test fun `exclusive catch end can label a payload boundary`() {
        val code = classWith(":start\nthrow v0\n:end\n:data\n.array-data 1\n0\n.end array-data\n:handler\nmove-exception v0\nreturn-void\n.catchall {:start .. :end} :handler")
        val a = SmaliAnalyzer.analyze(code)
        assertThat(a.errorCount).isEqualTo(0)
        val m = a.file.methods.single()
        val graph = com.riyadm.smalihelperplus.core.smali.analysis.ControlFlowAnalyzer.build(m)
        assertThat(graph.edges.map { it.kind }).contains(com.riyadm.smalihelperplus.core.smali.analysis.EdgeKind.EXCEPTION)
    }

}
