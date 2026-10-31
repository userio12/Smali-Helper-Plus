package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.smali.analysis.ControlFlowAnalyzer
import com.riyadm.smalihelperplus.core.smali.analysis.EdgeKind
import com.riyadm.smalihelperplus.core.smali.analysis.RegKey
import com.riyadm.smalihelperplus.core.smali.analysis.RegisterAnalyzer
import com.riyadm.smalihelperplus.core.smali.analysis.RegisterFrame
import com.riyadm.smalihelperplus.core.smali.model.Descriptors
import com.riyadm.smalihelperplus.core.smali.structure.StructureAnalyzer
import org.junit.Test

class SmaliAnalysisTest {
    private fun method(code: String, index: Int = 0) = SmaliAnalyzer.analyze(code).file.let { it to it.methods[index] }

    // ------------------------------------------------------------------ registers

    @Test
    fun `register usage reads and writes including wide pairs`() {
        val (file, m) = method(SmaliSamples.ARITHMETIC, 1)
        val a = RegisterAnalyzer.analyze(m, file.className)
        val add = a.usages[0] // add-long v0, p0, p2 ; locals 4 so p0 = v4, p2 = v6
        assertThat(add.writes).containsExactly(RegKey('v', 0), RegKey('v', 1))
        assertThat(add.reads).containsExactly(RegKey('v', 4), RegKey('v', 5), RegKey('v', 6), RegKey('v', 7))
        val div = a.usages[2] // div-long/2addr v0, v2
        assertThat(div.reads).containsAtLeast(RegKey('v', 0), RegKey('v', 2), RegKey('v', 3))
        assertThat(div.writes).containsExactly(RegKey('v', 0), RegKey('v', 1))
    }

    @Test
    fun `parameter mapping includes this and wide parameters`() {
        val code = classWith("return-void", signature = "f(JLjava/lang/String;)V", flags = "public", frame = ".locals 2")
        val (file, m) = method(code)
        val frame = RegisterFrame(m, file.className)
        val slots = frame.parameterSlots
        assertThat(slots.map { it.pRegister }).containsExactly("p0", "p1", "p2", "p3").inOrder()
        assertThat(slots.map { it.vRegister }).containsExactly("v2", "v3", "v4", "v5").inOrder()
        assertThat(slots[0].isThis).isTrue()
        assertThat(slots[2].isHighWord).isTrue()
        assertThat(slots[3].javaType).isEqualTo("String")
        assertThat(frame.display(RegKey('v', 3))).isEqualTo("v3 (p1)")
    }

    @Test
    fun `first definition, last use and live ranges`() {
        val a = SmaliAnalyzer.analyzeSnippet(SmaliSamples.SNIPPET)
        val r = RegisterAnalyzer.analyze(a.file.methods.single(), null)
        val v0 = r.summaries.getValue(RegKey('v', 0))
        assertThat(v0.firstDefinition).isEqualTo(0)
        assertThat(v0.lastUse).isEqualTo(2)
        assertThat(v0.liveRanges).containsExactly(0..2)
        val v2 = r.summaries.getValue(RegKey('v', 2))
        assertThat(v2.firstDefinition).isEqualTo(2)
        assertThat(v2.lastUse).isEqualTo(3)
        assertThat(v2.writes).isEqualTo(2)
        assertThat(v2.liveRanges).containsExactly(2..3)
    }

    @Test
    fun `liveness follows loop back edges`() {
        val (file, m) = method(SmaliSamples.LOOP)
        val r = RegisterAnalyzer.analyze(m, file.className)
        // v0 (the sum) stays live through the whole loop until the return.
        val v0 = r.summaries.getValue(RegKey('v', 0))
        assertThat(v0.liveRanges).containsExactly(0..8)
        // The array parameter (p0 = v4) is live at entry.
        val p0 = r.summaries.getValue(RegKey('v', 4))
        assertThat(p0.liveAtEntry).isTrue()
        assertThat(p0.parameter!!.name).isEqualTo("values")
    }

    @Test
    fun `range invokes read every register in the range`() {
        val a = SmaliAnalyzer.analyze(classWith("invoke-static/range {v0 .. v3}, LA;->b(IIII)V\nreturn-void"))
        val u = RegisterAnalyzer.analyze(a.file.methods.single(), null).usages[0]
        assertThat(u.reads).containsExactly(RegKey('v', 0), RegKey('v', 1), RegKey('v', 2), RegKey('v', 3))
    }

    // ------------------------------------------------------------------ control flow

    @Test
    fun `cfg for if`() {
        val (_, m) = method(SmaliSamples.ARITHMETIC, 0)
        val cfg = ControlFlowAnalyzer.build(m)
        assertThat(cfg.blocks).hasSize(3)
        assertThat(cfg.outgoing(0).map { it.to to it.label }).containsExactly(2 to "true", 1 to "false")
        assertThat(cfg.outgoing(1)).isEmpty()
    }

    @Test
    fun `cfg for loop with goto back edge`() {
        val (_, m) = method(SmaliSamples.LOOP)
        val cfg = ControlFlowAnalyzer.build(m)
        assertThat(cfg.blocks.map { it.startIndex to it.endIndex }).containsExactly(0 to 1, 2 to 3, 4 to 7, 8 to 8).inOrder()
        assertThat(cfg.outgoing(0).single().kind).isEqualTo(EdgeKind.FALLTHROUGH)
        assertThat(cfg.outgoing(1).map { it.to to it.kind }).containsExactly(3 to EdgeKind.BRANCH_TAKEN, 2 to EdgeKind.FALLTHROUGH)
        assertThat(cfg.outgoing(2).single().let { it.to to it.kind }).isEqualTo(1 to EdgeKind.GOTO)
        assertThat(cfg.blocks[1].labels).containsExactly("goto_0")
    }

    @Test
    fun `cfg for packed switch`() {
        val (_, m) = method(SmaliSamples.SWITCH)
        val cfg = ControlFlowAnalyzer.build(m)
        assertThat(cfg.blocks).hasSize(4)
        assertThat(cfg.outgoing(0).map { it.label to it.to }).containsExactly("case 1" to 2, "case 2" to 3, "default" to 1)
        assertThat(cfg.notes).isEmpty()
    }

    @Test
    fun `cfg for try catch adds exception edges`() {
        val (_, m) = method(SmaliSamples.TRY_CATCH)
        val cfg = ControlFlowAnalyzer.build(m)
        assertThat(cfg.blocks.map { it.startIndex }).containsExactly(0, 2, 3).inOrder()
        val exc = cfg.edges.single { it.kind == EdgeKind.EXCEPTION }
        assertThat(exc.from).isEqualTo(0)
        assertThat(exc.to).isEqualTo(2)
        assertThat(exc.label).isEqualTo("catch Ljava/lang/NumberFormatException;")
        assertThat(cfg.blocks[2].isHandler).isTrue()
        assertThat(cfg.outgoing(1)).isEmpty()
    }

    @Test
    fun `cfg for sparse switch and catchall`() {
        val code = classWith(
            """
            :try_start
            sparse-switch v0, :table
            :try_end
            .catchall {:try_start .. :try_end} :handler
            return-void
            :a
            return-void
            :handler
            return-void
            :table
            .sparse-switch
                -0x1 -> :a
                0x10 -> :a
            .end sparse-switch
            """,
        )
        val (_, m) = method(code)
        val cfg = ControlFlowAnalyzer.build(m)
        val out = cfg.outgoing(0).map { it.label }
        assertThat(out).containsExactly("case -1", "case 16", "default", "catch all")
    }

    // ------------------------------------------------------------------ structure

    @Test
    fun `structure outline decodes java signatures`() {
        val a = SmaliAnalyzer.analyze(SmaliSamples.STARTER)
        val o = StructureAnalyzer.outline(a.file)
        assertThat(o.javaName).isEqualTo("com.acme.demo.Greeter")
        assertThat(o.javaHeader).isEqualTo("public class Greeter")
        assertThat(o.fields.single().javaDeclaration).isEqualTo("private int count")
        assertThat(o.methods.map { it.javaSignature }).containsExactly("public Greeter()", "public String greet(String name)").inOrder()
        val greet = o.methods[1]
        assertThat(greet.registers).isEqualTo(5)
        assertThat(greet.locals).isEqualTo(3)
        assertThat(greet.instructionCount).isEqualTo(13)
        assertThat(greet.parameters.single().javaType).isEqualTo("String")
    }

    @Test
    fun `standalone method source keeps class header`() {
        val a = SmaliAnalyzer.analyze(SmaliSamples.STARTER)
        val src = StructureAnalyzer.standaloneMethodSource(SmaliSamples.STARTER, a.file, a.file.methods[1])
        assertThat(src).startsWith(".class public Lcom/acme/demo/Greeter;\n.super Ljava/lang/Object;\n.source \"Greeter.java\"\n\n.method public greet")
        assertThat(src.trimEnd()).endsWith(".end method")
        assertThat(SmaliAnalyzer.analyze(src).errorCount).isEqualTo(0)
    }

    @Test
    fun `descriptor helpers`() {
        assertThat(Descriptors.toJavaName("[[Ljava/lang/String;")).isEqualTo("java.lang.String[][]")
        assertThat(Descriptors.toJavaName("J")).isEqualTo("long")
        assertThat(Descriptors.parseProto("(IJ[Ljava/lang/Object;)V")!!.parameterWords).isEqualTo(4)
        assertThat(Descriptors.parseProto("(V)V")).isNull()
        assertThat(Descriptors.isValidClassDescriptor("Ljava//String;")).isFalse()
        assertThat(Descriptors.parseFieldReference("LA;->x:I")!!.name).isEqualTo("x")
        assertThat(Descriptors.parseMethodReference("[I->clone()Ljava/lang/Object;")!!.owner).isEqualTo("[I")
    }
}
