package com.riyadm.smalihelperplus.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegisterCalculatorTest {

    private fun ok(
        sig: String,
        static: Boolean,
        count: Int,
        decl: RegisterDeclaration = RegisterDeclaration.LOCALS,
        cls: String? = null,
    ): RegisterLayout {
        val r = RegisterCalculator.calculate(sig, static, decl, count, cls)
        assertThat(r).isInstanceOf(RegisterResult.Success::class.java)
        return (r as RegisterResult.Success).layout
    }

    @Test fun staticMethodNoParams() {
        val l = ok("()V", true, 3)
        assertThat(l.parameterWords).isEqualTo(0)
        assertThat(l.totalRegisters).isEqualTo(3)
        assertThat(l.slots).isEmpty()
    }

    @Test fun instanceMethodHasThisAtP0() {
        val l = ok("(I)V", false, 2)
        assertThat(l.parameterWords).isEqualTo(2)
        assertThat(l.totalRegisters).isEqualTo(4)
        assertThat(l.slots[0].isThis).isTrue()
        assertThat(l.slots[0].pName).isEqualTo("p0")
        assertThat(l.slots[0].vName).isEqualTo("v2")
        assertThat(l.slots[1].pName).isEqualTo("p1")
        assertThat(l.slots[1].vName).isEqualTo("v3")
    }

    @Test fun wideParametersUseTwoRegisters() {
        val l = ok("(IJD)V", true, 1)
        assertThat(l.parameterWords).isEqualTo(5)
        assertThat(l.totalRegisters).isEqualTo(6)
        val j = l.slots[1]
        assertThat(j.isWide).isTrue()
        assertThat(j.firstP).isEqualTo(1)
        assertThat(j.firstV).isEqualTo(2)
        assertThat(j.lastV).isEqualTo(3)
        val d = l.slots[2]
        assertThat(d.firstP).isEqualTo(3)
        assertThat(d.firstV).isEqualTo(4)
    }

    @Test fun wideRowsAreMarked() {
        val l = ok("(J)V", false, 0)
        val rows = l.rows.filter { it.p != null }
        assertThat(rows.map { it.pName }).containsExactly("p0", "p1", "p2").inOrder()
        assertThat(rows[1].isWideLow).isTrue()
        assertThat(rows[2].isWideHigh).isTrue()
    }

    @Test fun registersDeclarationDerivesLocals() {
        val l = ok("(IJ)V", false, 7, RegisterDeclaration.REGISTERS)
        assertThat(l.parameterWords).isEqualTo(4)
        assertThat(l.locals).isEqualTo(3)
        assertThat(l.totalRegisters).isEqualTo(7)
        assertThat(l.localsDirective).isEqualTo(".locals 3")
    }

    @Test fun registersTooSmallIsAnError() {
        val r = RegisterCalculator.calculate("(IJ)V", true, RegisterDeclaration.REGISTERS, 2)
        assertThat(r).isInstanceOf(RegisterResult.Failure::class.java)
        assertThat((r as RegisterResult.Failure).message).contains("too small")
    }

    @Test fun parametersAboveV15AreFlagged() {
        val l = ok("(IIII)V", true, 14)
        // locals 14 -> params at v14..v17
        assertThat(l.slots.map { it.beyond4Bit }).containsExactly(false, false, true, true).inOrder()
        assertThat(l.slotsBeyond4Bit).hasSize(2)
        assertThat(l.notes.any { it.level == NoteLevel.WARNING && it.text.contains("v15") }).isTrue()
    }

    @Test fun parametersAboveV255AreFlagged() {
        val l = ok("(II)V", true, 255)
        assertThat(l.slots[0].vName).isEqualTo("v255")
        assertThat(l.slots[0].beyond8Bit).isFalse()
        assertThat(l.slots[1].beyond8Bit).isTrue()
        assertThat(l.notes.any { it.text.contains("v255") }).isTrue()
    }

    @Test fun wideStraddlingBoundaryCountsAsBeyond() {
        val l = ok("(J)V", true, 15)
        // pair is v15/v16 -> second half is beyond v15
        assertThat(l.slots[0].firstV).isEqualTo(15)
        assertThat(l.slots[0].beyond4Bit).isTrue()
    }

    @Test fun invokeLimitNoteForManyParameters() {
        val l = ok("(IIIIII)V", true, 0)
        assertThat(l.notes.any { it.text.contains("/range") }).isTrue()
    }

    @Test fun javaStyleParameterList() {
        val l = ok("String name, long id", false, 2)
        assertThat(l.parameterWords).isEqualTo(4)
        assertThat(l.slots[1].label).isEqualTo("name")
        assertThat(l.slots[2].label).isEqualTo("id")
    }

    @Test fun methodReferenceInput() {
        val l = ok("Lcom/a/B;->m(DI)V", true, 0)
        assertThat(l.parameterWords).isEqualTo(3)
    }

    @Test fun declaringClassIsValidated() {
        val bad = RegisterCalculator.calculate("()V", false, RegisterDeclaration.LOCALS, 0, "com.a.B")
        assertThat(bad).isInstanceOf(RegisterResult.Failure::class.java)
        val good = ok("()V", false, 0, cls = "Lcom/a/B;")
        assertThat(good.slots[0].type.java).isEqualTo("com.a.B")
    }

    @Test fun invalidSignatureReportsFailure() {
        val r = RegisterCalculator.calculate("(Q)V", true, RegisterDeclaration.LOCALS, 0)
        assertThat(r).isInstanceOf(RegisterResult.Failure::class.java)
    }

    @Test fun negativeAndHugeCountsRejected() {
        assertThat(RegisterCalculator.calculate("()V", true, RegisterDeclaration.LOCALS, -1)).isInstanceOf(RegisterResult.Failure::class.java)
        assertThat(RegisterCalculator.calculate("()V", true, RegisterDeclaration.LOCALS, 65536)).isInstanceOf(RegisterResult.Failure::class.java)
        // 65535 locals + 1 parameter word exceeds the register limit
        assertThat(RegisterCalculator.calculate("(I)V", true, RegisterDeclaration.LOCALS, 65535)).isInstanceOf(RegisterResult.Failure::class.java)
        assertThat(RegisterCalculator.calculate("(I)V", true, RegisterDeclaration.LOCALS, 65534)).isInstanceOf(RegisterResult.Success::class.java)
    }

    @Test fun rowsCoverEveryRegisterForSmallFrames() {
        val l = ok("(IJ)V", false, 4)
        assertThat(l.rows.map { it.v }).containsExactlyElementsIn((0 until l.totalRegisters).toList()).inOrder()
        assertThat(l.rows.count { it.p == null }).isEqualTo(4)
    }
}
