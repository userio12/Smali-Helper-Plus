package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity
import org.junit.Test

class SmaliValidatorTest {
    @Test
    fun `all built-in samples are free of errors and warnings`() {
        SmaliSamples.all.forEach { sample ->
            val result = if (sample.id == "snippet") SmaliAnalyzer.analyzeSnippet(sample.code) else SmaliAnalyzer.analyze(sample.code)
            assertWithMessage(sample.id).that(result.diagnostics.filter { it.severity != Severity.INFO }).isEmpty()
        }
    }

    @Test
    fun `unknown opcode suggests the closest mnemonic`() {
        val d = diagnostics(classWith("cosnt/4 v0, 0x1\nreturn-void")).withCode(DiagnosticCode.UNKNOWN_OPCODE).single()
        assertThat(d.suggestion).contains("const/4")
        assertThat(d.line).isEqualTo(6)
        assertThat(d.startColumn).isEqualTo(4)
        assertThat(d.endColumn).isEqualTo(11)
    }

    @Test
    fun `wrong operand count is reported`() {
        val d = diagnostics(classWith("const/4 v0\nreturn-void"))
        assertThat(d.codes()).contains(DiagnosticCode.OPERAND_COUNT)
    }

    @Test
    fun `wrong operand kind is reported`() {
        val d = diagnostics(classWith("const/4 v0, v1\nreturn-void"))
        assertThat(d.codes()).contains(DiagnosticCode.OPERAND_KIND)
        val d2 = diagnostics(classWith("move v0, 0x1\nreturn-void"))
        assertThat(d2.codes()).contains(DiagnosticCode.OPERAND_KIND)
    }

    @Test
    fun `register beyond 4-bit limit suggests from16 for moves`() {
        val d = diagnostics(classWith("move v16, v0\nreturn-void", frame = ".registers 20")).withCode(DiagnosticCode.REGISTER_WIDTH).single()
        assertThat(d.suggestion).contains("move/from16")
        assertThat(d.message).contains("v0–v15")
    }

    @Test
    fun `register beyond 4-bit limit in 22c instruction suggests moving first`() {
        val code = classWith("iget v0, v16, LA;->f:I\nreturn-void", frame = ".registers 20")
        val d = diagnostics(code).withCode(DiagnosticCode.REGISTER_WIDTH).single()
        assertThat(d.suggestion).contains("Move the value")
    }

    @Test
    fun `parameter register aliasing a high v register is checked against the width`() {
        // 20 locals: p0 is v20, which does not fit the 4-bit field of iget.
        val code = classWith("iget v0, p0, LA;->f:I\nreturn-void", signature = "test()V", flags = "public", frame = ".locals 20")
        val d = diagnostics(code).withCode(DiagnosticCode.REGISTER_WIDTH).single()
        assertThat(d.message).contains("v20")
    }

    @Test
    fun `register outside the frame is reported`() {
        val d = diagnostics(classWith("const/4 v4, 0x1\nreturn-void", frame = ".registers 4"))
        assertThat(d.codes()).contains(DiagnosticCode.REGISTER_OUT_OF_FRAME)
    }

    @Test
    fun `frame accounts for this and wide parameters`() {
        // Instance method (J)V with .locals 1: v0 local, p0=this, p1/p2 = long -> 4 registers in total.
        val ok = classWith("return-void", signature = "test(J)V", flags = "public", frame = ".locals 1")
        assertThat(diagnostics(ok).errors()).isEmpty()
        val bad = classWith("move-wide v0, p3\nreturn-void", signature = "test(J)V", flags = "public", frame = ".locals 1")
        assertThat(diagnostics(bad).codes()).contains(DiagnosticCode.PARAMETER_OUT_OF_RANGE)
    }

    @Test
    fun `parameter register beyond parameter count`() {
        val d = diagnostics(classWith("return p1", signature = "test(I)I", frame = ".registers 2"))
        val diag = d.withCode(DiagnosticCode.PARAMETER_OUT_OF_RANGE).single()
        assertThat(diag.message).contains("p1")
    }

    @Test
    fun `wide register pair overflowing the frame`() {
        val d = diagnostics(classWith("const-wide/16 v3, 0x1\nreturn-void", frame = ".registers 4"))
        assertThat(d.codes()).contains(DiagnosticCode.WIDE_PAIR_OVERFLOW)
    }

    @Test
    fun `const 4 literal range`() {
        assertThat(diagnostics(classWith("const/4 v0, 0x7\nreturn-void")).errors()).isEmpty()
        assertThat(diagnostics(classWith("const/4 v0, -0x8\nreturn-void")).errors()).isEmpty()
        val d = diagnostics(classWith("const/4 v0, 0x8\nreturn-void")).withCode(DiagnosticCode.LITERAL_RANGE).single()
        assertThat(d.suggestion).contains("const/16")
        assertThat(diagnostics(classWith("const/4 v0, -9\nreturn-void")).codes()).contains(DiagnosticCode.LITERAL_RANGE)
    }

    @Test
    fun `const 16 and lit8 and lit16 ranges`() {
        assertThat(diagnostics(classWith("const/16 v0, 32767\nreturn-void")).errors()).isEmpty()
        assertThat(diagnostics(classWith("const/16 v0, 0xffff\nreturn-void")).errors()).isEmpty() // hex bit pattern for -1
        assertThat(diagnostics(classWith("const/16 v0, 32768\nreturn-void")).errors()).isEmpty()
        assertThat(diagnostics(classWith("add-int/lit8 v0, v1, 200\nreturn-void")).errors()).isEmpty()
        assertThat(diagnostics(classWith("add-int/lit16 v0, v1, 40000\nreturn-void")).errors()).isEmpty()
        val lit8 = diagnostics(classWith("add-int/lit8 v0, v1, 256\nreturn-void")).withCode(DiagnosticCode.LITERAL_RANGE).single()
        assertThat(lit8.suggestion).contains("add-int/lit16")
        assertThat(diagnostics(classWith("add-int/lit16 v0, v1, 65536\nreturn-void")).codes()).contains(DiagnosticCode.LITERAL_RANGE)
    }

    @Test
    fun `const high16 requires zero low bits`() {
        assertThat(diagnostics(classWith("const/high16 v0, 0x7f010000\nreturn-void")).errors()).isEmpty()
        assertThat(diagnostics(classWith("const/high16 v0, 10.0f\nreturn-void")).errors()).isEmpty()
        val d = diagnostics(classWith("const/high16 v0, 0x7f010001\nreturn-void")).withCode(DiagnosticCode.LITERAL_RANGE).single()
        assertThat(d.suggestion).contains("const")
        assertThat(diagnostics(classWith("const-wide/high16 v0, 0x4024000000000000L\nreturn-void")).errors()).isEmpty()
        assertThat(diagnostics(classWith("const-wide/high16 v0, 0x4024000000000001L\nreturn-void")).codes()).contains(DiagnosticCode.LITERAL_RANGE)
    }

    @Test
    fun `invalid descriptors are reported`() {
        assertThat(diagnostics(classWith("new-instance v0, Ljava/lang/Object\nreturn-void")).codes()).contains(DiagnosticCode.INVALID_DESCRIPTOR)
        assertThat(diagnostics(classWith("sget v0, LA;->f\nreturn-void")).codes()).contains(DiagnosticCode.INVALID_DESCRIPTOR)
        assertThat(diagnostics(classWith("invoke-static {}, LA;->b(Q)V\nreturn-void")).codes()).contains(DiagnosticCode.INVALID_DESCRIPTOR)
        assertThat(diagnostics(classWith("new-instance v0, [I\nreturn-void")).codes()).contains(DiagnosticCode.INVALID_DESCRIPTOR)
        assertThat(diagnostics(classWith("check-cast v0, I\nreturn-void")).codes()).contains(DiagnosticCode.INVALID_DESCRIPTOR)
    }

    @Test
    fun `field type mismatch is a warning`() {
        val d = diagnostics(classWith("sget v0, LA;->f:Ljava/lang/String;\nreturn-void"))
        val w = d.single { it.code == DiagnosticCode.OPERAND_KIND }
        assertThat(w.severity).isEqualTo(Severity.WARNING)
        assertThat(w.suggestion).contains("sget-object")
    }

    @Test
    fun `undefined and duplicate labels`() {
        val undefined = diagnostics(classWith("goto :cond_1\n:cond_0\nreturn-void")).withCode(DiagnosticCode.UNDEFINED_LABEL).single()
        assertThat(undefined.suggestion).contains(":cond_0")
        val dup = diagnostics(classWith(":a\nnop\n:a\ngoto :a"))
        assertThat(dup.codes()).contains(DiagnosticCode.DUPLICATE_LABEL)
    }

    @Test
    fun `missing end method and stray end`() {
        val code = ".class public LA;\n.super Ljava/lang/Object;\n.method public static a()V\n    .registers 1\n    return-void\n"
        val d = diagnostics(code).withCode(DiagnosticCode.MISSING_END).single()
        assertThat(d.line).isEqualTo(3)
        val two = ".class public LA;\n.super Ljava/lang/Object;\n.method public static a()V\n.registers 1\nreturn-void\n.method public static b()V\n.registers 1\nreturn-void\n.end method\n"
        assertThat(diagnostics(two).codes()).contains(DiagnosticCode.MISSING_END)
        val stray = ".class public LA;\n.super Ljava/lang/Object;\n.end method\n"
        assertThat(diagnostics(stray).codes()).contains(DiagnosticCode.UNEXPECTED_END)
    }

    @Test
    fun `missing end annotation and end of payload`() {
        val annotation = ".class public LA;\n.super Ljava/lang/Object;\n.annotation runtime LB;\n    value = 1\n.method public static a()V\n.registers 1\nreturn-void\n.end method\n"
        assertThat(diagnostics(annotation).codes()).contains(DiagnosticCode.MISSING_END)
        val payload = classWith("packed-switch v0, :p\nreturn-void\n:p\n.packed-switch 0x0\n:a\n:a\nreturn-void")
        assertThat(diagnostics(payload).codes()).contains(DiagnosticCode.MISSING_END)
    }

    @Test
    fun `missing class and super`() {
        val d = diagnostics(".method public static a()V\n.registers 1\nreturn-void\n.end method\n")
        assertThat(d.codes()).containsAtLeast(DiagnosticCode.MISSING_CLASS, DiagnosticCode.MISSING_SUPER)
        val obj = diagnostics(".class public Ljava/lang/Object;\n")
        assertThat(obj.codes()).doesNotContain(DiagnosticCode.MISSING_SUPER)
    }

    @Test
    fun `registers and locals together`() {
        val code = classWith(".locals 1\nreturn-void", frame = ".registers 2")
        assertThat(diagnostics(code).codes()).contains(DiagnosticCode.REGISTERS_AND_LOCALS)
    }

    @Test
    fun `missing registers directive`() {
        assertThat(diagnostics(classWith("return-void", frame = "")).codes()).contains(DiagnosticCode.MISSING_REGISTERS)
    }

    @Test
    fun `return opcode must match the return type`() {
        val voidMethod = diagnostics(classWith("const/4 v0, 0x0\nreturn v0")).withCode(DiagnosticCode.RETURN_MISMATCH).single()
        assertThat(voidMethod.suggestion).contains("return-void")
        val intMethod = diagnostics(classWith("return-void", signature = "test()I")).withCode(DiagnosticCode.RETURN_MISMATCH).single()
        assertThat(intMethod.suggestion).contains("`return`")
        val longMethod = diagnostics(classWith("const-wide/16 v0, 0x0\nreturn v0", signature = "test()J")).withCode(DiagnosticCode.RETURN_MISMATCH).single()
        assertThat(longMethod.suggestion).contains("return-wide")
        val objMethod = diagnostics(classWith("const/4 v0, 0x0\nreturn v0", signature = "test()[I")).withCode(DiagnosticCode.RETURN_MISMATCH).single()
        assertThat(objMethod.suggestion).contains("return-object")
    }

    @Test
    fun `range syntax and contiguity`() {
        val backwards = diagnostics(classWith("invoke-static/range {v3 .. v0}, LA;->b(IIII)V\nreturn-void"))
        assertThat(backwards.codes()).contains(DiagnosticCode.RANGE_SYNTAX)
        val listForRange = diagnostics(classWith("invoke-static/range {v0, v1}, LA;->b(II)V\nreturn-void"))
        assertThat(listForRange.codes()).contains(DiagnosticCode.RANGE_SYNTAX)
        val rangeForList = diagnostics(classWith("invoke-static {v0 .. v1}, LA;->b(II)V\nreturn-void"))
        assertThat(rangeForList.codes()).contains(DiagnosticCode.RANGE_SYNTAX)
        val ok = diagnostics(classWith("invoke-static/range {v0 .. v3}, LA;->b(IIII)V\nreturn-void"))
        assertThat(ok.errors()).isEmpty()
    }

    @Test
    fun `more than five registers needs the range form`() {
        val code = classWith("invoke-static {v0, v1, v2, v3, v4, v5}, LA;->b(IIIIII)V\nreturn-void", frame = ".registers 6")
        val d = diagnostics(code).withCode(DiagnosticCode.TOO_MANY_REGISTERS).single()
        assertThat(d.suggestion).contains("invoke-static/range")
    }

    @Test
    fun `argument word count must match the prototype`() {
        val d = diagnostics(classWith("invoke-static {v0}, LA;->b(J)V\nreturn-void"))
        assertThat(d.codes()).contains(DiagnosticCode.ARGUMENT_COUNT)
        val virtual = diagnostics(classWith("invoke-virtual {v0}, LA;->b(I)V\nreturn-void"))
        assertThat(virtual.codes()).contains(DiagnosticCode.ARGUMENT_COUNT)
        val pair = diagnostics(classWith("invoke-static {v0, v2}, LA;->b(J)V\nreturn-void"))
        assertThat(pair.codes()).contains(DiagnosticCode.ARGUMENT_COUNT)
    }

    @Test
    fun `move-result must directly follow an invoke`() {
        val orphan = diagnostics(classWith("const/4 v0, 0x1\nmove-result v0\nreturn-void"))
        assertThat(orphan.codes()).contains(DiagnosticCode.MOVE_RESULT_PLACEMENT)
        val voidCall = diagnostics(classWith("invoke-static {}, LA;->b()V\nmove-result v0\nreturn-void"))
        assertThat(voidCall.codes()).contains(DiagnosticCode.MOVE_RESULT_PLACEMENT)
        val wrongKind = diagnostics(classWith("invoke-static {}, LA;->b()Ljava/lang/String;\nmove-result v0\nreturn-void"))
        assertThat(wrongKind.withCode(DiagnosticCode.MOVE_RESULT_PLACEMENT).single().suggestion).contains("move-result-object")
        val ok = diagnostics(classWith("filled-new-array {v0, v1}, [I\nmove-result-object v2\nreturn-void"))
        assertThat(ok.errors()).isEmpty()
    }

    @Test
    fun `instructions outside methods`() {
        val d = diagnostics(".class public LA;\n.super Ljava/lang/Object;\nconst/4 v0, 0x1\n")
        assertThat(d.withCode(DiagnosticCode.OUTSIDE_METHOD).single().line).isEqualTo(3)
    }

    @Test
    fun `api level warnings depend on minApi`() {
        val code = classWith("const-method-type v0, (I)V\nreturn-void")
        val w = diagnostics(code, minApi = 26).withCode(DiagnosticCode.API_LEVEL).single()
        assertThat(w.severity).isEqualTo(Severity.WARNING)
        assertThat(w.message).contains("API 28")
        assertThat(diagnostics(code, minApi = 28).codes()).doesNotContain(DiagnosticCode.API_LEVEL)
    }

    @Test
    fun `execution falling off the end`() {
        assertThat(diagnostics(classWith("const/4 v0, 0x1")).codes()).contains(DiagnosticCode.FALLS_OFF_END)
    }

    @Test
    fun `switch must target a matching payload`() {
        val code = classWith("packed-switch v0, :p\nreturn-void\n:p\n.sparse-switch\n0x1 -> :a\n.end sparse-switch\n:a\nreturn-void")
        assertThat(diagnostics(code).codes()).contains(DiagnosticCode.PAYLOAD_MISMATCH)
    }

    @Test
    fun `abstract methods cannot have code and concrete methods need code`() {
        val abstractCode = ".class public abstract LA;\n.super Ljava/lang/Object;\n.method public abstract a()V\n    return-void\n.end method\n"
        assertThat(diagnostics(abstractCode).codes()).contains(DiagnosticCode.CODE_IN_ABSTRACT)
        val empty = ".class public LA;\n.super Ljava/lang/Object;\n.method public a()V\n    .registers 1\n.end method\n"
        assertThat(diagnostics(empty).codes()).contains(DiagnosticCode.MISSING_CODE)
    }

    @Test
    fun `unknown directive and unterminated string`() {
        val d = diagnostics(classWith(".lcals 2\nreturn-void"))
        assertThat(d.withCode(DiagnosticCode.UNKNOWN_DIRECTIVE).single().suggestion).contains(".locals")
        val s = diagnostics(classWith("const-string v0, \"oops\nreturn-void"))
        assertThat(s.codes()).contains(DiagnosticCode.UNTERMINATED_LITERAL)
    }
}
