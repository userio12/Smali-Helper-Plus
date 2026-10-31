package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.smali.opcodes.DalvikOpcodeTable
import com.riyadm.smalihelperplus.core.smali.opcodes.InstructionFormat
import com.riyadm.smalihelperplus.core.smali.opcodes.LiteralKind
import com.riyadm.smalihelperplus.core.smali.opcodes.OperandSpec
import com.riyadm.smalihelperplus.core.smali.opcodes.ReferenceKind
import org.junit.Test

class DalvikOpcodeTableTest {
    @Test
    fun `table covers every assigned opcode value exactly once`() {
        val values = DalvikOpcodeTable.all.map { it.value }
        assertThat(values).containsNoDuplicates()
        assertThat(values.size).isEqualTo(224)
        val expected = (0x00..0xff).filter { it !in DalvikOpcodeTable.unusedValues }
        assertThat(values).containsExactlyElementsIn(expected)
        assertThat(DalvikOpcodeTable.mnemonics).containsNoDuplicates()
    }

    @Test
    fun `spot check values and formats against the specification`() {
        fun check(name: String, value: Int, format: InstructionFormat) {
            val op = DalvikOpcodeTable.byMnemonic(name)!!
            assertThat(op.value).isEqualTo(value)
            assertThat(op.format).isEqualTo(format)
        }
        check("nop", 0x00, InstructionFormat.F10x)
        check("move/from16", 0x02, InstructionFormat.F22x)
        check("move-result-object", 0x0c, InstructionFormat.F11x)
        check("const/4", 0x12, InstructionFormat.F11n)
        check("const-wide", 0x18, InstructionFormat.F51l)
        check("const-string/jumbo", 0x1b, InstructionFormat.F31c)
        check("filled-new-array/range", 0x25, InstructionFormat.F3rc)
        check("sparse-switch", 0x2c, InstructionFormat.F31t)
        check("if-le", 0x37, InstructionFormat.F22t)
        check("if-lez", 0x3d, InstructionFormat.F21t)
        check("aput-short", 0x51, InstructionFormat.F23x)
        check("iput-short", 0x5f, InstructionFormat.F22c)
        check("sput-short", 0x6d, InstructionFormat.F21c)
        check("invoke-interface", 0x72, InstructionFormat.F35c)
        check("invoke-interface/range", 0x78, InstructionFormat.F3rc)
        check("int-to-short", 0x8f, InstructionFormat.F12x)
        check("rem-double", 0xaf, InstructionFormat.F23x)
        check("rem-double/2addr", 0xcf, InstructionFormat.F12x)
        check("rsub-int", 0xd1, InstructionFormat.F22s)
        check("ushr-int/lit8", 0xe2, InstructionFormat.F22b)
        check("invoke-polymorphic", 0xfa, InstructionFormat.F45cc)
        check("invoke-polymorphic/range", 0xfb, InstructionFormat.F4rcc)
        check("invoke-custom/range", 0xfd, InstructionFormat.F3rc)
        check("const-method-type", 0xff, InstructionFormat.F21c)
    }

    @Test
    fun `min api levels follow dex versions 038 and 039`() {
        val api26 = listOf("invoke-polymorphic", "invoke-polymorphic/range", "invoke-custom", "invoke-custom/range")
        val api28 = listOf("const-method-handle", "const-method-type")
        DalvikOpcodeTable.all.forEach { op ->
            val expected = when (op.mnemonic) {
                in api26 -> 26
                in api28 -> 28
                else -> 1
            }
            assertThat(op.minApi).isEqualTo(expected)
        }
    }

    @Test
    fun `register widths match the format`() {
        val move = DalvikOpcodeTable.byMnemonic("move")!!.registerOperands
        assertThat(move.map { it.bits }).containsExactly(4, 4)
        val from16 = DalvikOpcodeTable.byMnemonic("move/from16")!!.registerOperands
        assertThat(from16.map { it.bits }).containsExactly(8, 16).inOrder()
        val iget = DalvikOpcodeTable.byMnemonic("iget-object")!!.registerOperands
        assertThat(iget.map { it.bits }).containsExactly(4, 4)
        val sget = DalvikOpcodeTable.byMnemonic("sget")!!.registerOperands
        assertThat(sget.single().bits).isEqualTo(8)
    }

    @Test
    fun `wide operands are flagged`() {
        assertThat(DalvikOpcodeTable.byMnemonic("move-wide")!!.registerOperands.all { it.wide }).isTrue()
        val shl = DalvikOpcodeTable.byMnemonic("shl-long")!!.registerOperands
        assertThat(shl.map { it.wide }).containsExactly(true, true, false).inOrder()
        val conv = DalvikOpcodeTable.byMnemonic("int-to-long")!!.registerOperands
        assertThat(conv.map { it.wide }).containsExactly(true, false).inOrder()
        val cmp = DalvikOpcodeTable.byMnemonic("cmp-long")!!.registerOperands
        assertThat(cmp.map { it.wide }).containsExactly(false, true, true).inOrder()
    }

    @Test
    fun `literal and reference shapes`() {
        val c4 = DalvikOpcodeTable.byMnemonic("const/4")!!.operands[1] as OperandSpec.Literal
        assertThat(c4.bits).isEqualTo(4)
        assertThat(c4.kind).isEqualTo(LiteralKind.SIGNED)
        val high = DalvikOpcodeTable.byMnemonic("const-wide/high16")!!.operands[1] as OperandSpec.Literal
        assertThat(high.kind).isEqualTo(LiteralKind.WIDE_HIGH16)
        val poly = DalvikOpcodeTable.byMnemonic("invoke-polymorphic")!!.operands
        assertThat(poly.filterIsInstance<OperandSpec.Reference>().map { it.kind })
            .containsExactly(ReferenceKind.METHOD, ReferenceKind.PROTO).inOrder()
        val handle = DalvikOpcodeTable.byMnemonic("const-method-handle")!!.operands[1] as OperandSpec.Reference
        assertThat(handle.kind).isEqualTo(ReferenceKind.METHOD_HANDLE)
    }

    @Test
    fun `did you mean suggestions use edit distance`() {
        assertThat(DalvikOpcodeTable.suggest("cosnt/4")).contains("const/4")
        assertThat(DalvikOpcodeTable.suggest("invoke-virtul").first()).isEqualTo("invoke-virtual")
        assertThat(DalvikOpcodeTable.editDistance("kitten", "sitting")).isEqualTo(3)
    }
    @Test
    fun `all instructions agree with bundled opcode documentation`() {
        val file = listOf(java.io.File("src/main/assets/content/opcodes.json"),
            java.io.File("app/src/main/assets/content/opcodes.json")).first { it.isFile }
        val bundle = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(com.riyadm.smalihelperplus.core.content.OpcodeBundleDto.serializer(), file.readText())
        val instructions = bundle.opcodes.filter { it.opcode <= 255 }
        assertThat(instructions).hasSize(224)
        assertThat(instructions.map { it.name }).containsExactlyElementsIn(DalvikOpcodeTable.mnemonics)
        instructions.forEach { documented ->
            val op = DalvikOpcodeTable.byMnemonic(documented.name)!!
            assertThat(op.value).isEqualTo(documented.opcode)
            assertThat(op.format.id).isEqualTo(documented.format)
            assertThat(op.minApi).isEqualTo(documented.minApi)
        }
    }

}
