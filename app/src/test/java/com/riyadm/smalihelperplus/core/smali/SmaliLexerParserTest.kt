package com.riyadm.smalihelperplus.core.smali

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.smali.ast.CatchDirective
import com.riyadm.smalihelperplus.core.smali.ast.LabelOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralOperand
import com.riyadm.smalihelperplus.core.smali.ast.LiteralType
import com.riyadm.smalihelperplus.core.smali.ast.PayloadDirective
import com.riyadm.smalihelperplus.core.smali.ast.ReferenceOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterListOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterRangeOperand
import com.riyadm.smalihelperplus.core.smali.ast.StringOperand
import com.riyadm.smalihelperplus.core.smali.diagnostics.DiagnosticCode
import com.riyadm.smalihelperplus.core.smali.lexer.SmaliLexer
import com.riyadm.smalihelperplus.core.smali.lexer.TokenType
import com.riyadm.smalihelperplus.core.smali.opcodes.PayloadKind
import com.riyadm.smalihelperplus.core.smali.parser.ParseMode
import com.riyadm.smalihelperplus.core.smali.parser.SmaliParser
import org.junit.Test
import java.math.BigInteger

class SmaliLexerParserTest {
    @Test
    fun `lexer classifies tokens with positions`() {
        val tokens = SmaliLexer.lexLine("    invoke-virtual {v0, p1}, Ljava/lang/Object;->toString()Ljava/lang/String; # call", 3)
        assertThat(tokens.map { it.type }).containsExactly(
            TokenType.WORD, TokenType.LBRACE, TokenType.REGISTER, TokenType.COMMA, TokenType.REGISTER,
            TokenType.RBRACE, TokenType.COMMA, TokenType.WORD, TokenType.COMMENT,
        ).inOrder()
        assertThat(tokens[0].column).isEqualTo(4)
        assertThat(tokens[0].endColumn).isEqualTo(18)
        assertThat(tokens[0].line).isEqualTo(3)
        assertThat(tokens.last().text).isEqualTo("# call")
    }

    @Test
    fun `lexer keeps hash inside strings and flags unterminated strings`() {
        val ok = SmaliLexer.lexLine("const-string v0, \"a # not comment\"", 1)
        assertThat(ok.map { it.type }).containsExactly(TokenType.WORD, TokenType.REGISTER, TokenType.COMMA, TokenType.STRING).inOrder()
        val escaped = SmaliLexer.lexLine("const-string v0, \"say \\\"hi\\\"\"", 1)
        assertThat(escaped.last().type).isEqualTo(TokenType.STRING)
        val bad = SmaliLexer.lexLine("const-string v0, \"oops", 1)
        assertThat(bad.last().type).isEqualTo(TokenType.ERROR)
    }

    @Test
    fun `lexer splits compact register ranges and recognises numbers`() {
        val tokens = SmaliLexer.lexLine("invoke-static/range {v0..v3}, LA;->b(IIII)V", 1)
        assertThat(tokens.map { it.type }.take(6)).containsExactly(
            TokenType.WORD, TokenType.LBRACE, TokenType.REGISTER, TokenType.DOTDOT, TokenType.REGISTER, TokenType.RBRACE,
        ).inOrder()
        val nums = SmaliLexer.lexLine("const v0, -0x10", 1)
        assertThat(nums.last().type).isEqualTo(TokenType.NUMBER)
        assertThat(SmaliLexer.lexLine("const v0, 1.5f", 1).last().type).isEqualTo(TokenType.NUMBER)
    }

    @Test
    fun `parser builds class structure`() {
        val result = SmaliParser.parse(SmaliSamples.STARTER)
        val file = result.file
        assertThat(file.header!!.descriptor).isEqualTo("Lcom/acme/demo/Greeter;")
        assertThat(file.header!!.flags).containsExactly("public")
        assertThat(file.superClass!!.descriptor).isEqualTo("Ljava/lang/Object;")
        assertThat(file.source).isEqualTo("Greeter.java")
        assertThat(file.fields.single().name).isEqualTo("count")
        assertThat(file.fields.single().type).isEqualTo("I")
        assertThat(file.methods.map { it.name }).containsExactly("<init>", "greet").inOrder()
        val greet = file.methods[1]
        assertThat(greet.registers!!.value).isEqualTo(5)
        assertThat(greet.proto!!.parameters).containsExactly("Ljava/lang/String;")
        assertThat(greet.proto!!.returnType).isEqualTo("Ljava/lang/String;")
        assertThat(greet.instructions).hasSize(13)
        assertThat(result.diagnostics).isEmpty()
    }

    @Test
    fun `parser decodes operand kinds`() {
        val code = classWith(
            """
            const/4 v0, 0x1
            const-string v1, "hi"
            invoke-static {v0, v1}, LA;->b(ILjava/lang/String;)V
            invoke-static/range {v0 .. v1}, LA;->b(ILjava/lang/String;)V
            if-eqz v0, :end
            :end
            return-void
            """,
        )
        val m = SmaliParser.parse(code).file.methods.single()
        val insns = m.instructions
        assertThat(insns[0].operands[0]).isInstanceOf(RegisterOperand::class.java)
        val lit = insns[0].operands[1] as LiteralOperand
        assertThat(lit.value).isEqualTo(BigInteger.ONE)
        assertThat(lit.radixPrefixed).isTrue()
        assertThat(insns[1].operands[1]).isInstanceOf(StringOperand::class.java)
        assertThat((insns[2].operands[0] as RegisterListOperand).registers.map { it.text }).containsExactly("v0", "v1").inOrder()
        assertThat(insns[2].operands[1]).isInstanceOf(ReferenceOperand::class.java)
        val range = insns[3].operands[0] as RegisterRangeOperand
        assertThat(range.first.text).isEqualTo("v0")
        assertThat(range.last.text).isEqualTo("v1")
        assertThat(insns[4].operands[1]).isInstanceOf(LabelOperand::class.java)
    }

    @Test
    fun `parser handles catch and payload directives`() {
        val tryFile = SmaliParser.parse(SmaliSamples.TRY_CATCH).file.methods.single()
        val catch = tryFile.items.filterIsInstance<CatchDirective>().single()
        assertThat(catch.exceptionType).isEqualTo("Ljava/lang/NumberFormatException;")
        assertThat(catch.start.name).isEqualTo("try_start_0")
        assertThat(catch.handler.name).isEqualTo("catch_0")
        val sw = SmaliParser.parse(SmaliSamples.SWITCH).file.methods.single()
        val payload = sw.items.filterIsInstance<PayloadDirective>().single()
        assertThat(payload.kind).isEqualTo(PayloadKind.PACKED_SWITCH)
        assertThat(payload.firstKey).isEqualTo(1L)
        assertThat(payload.targets.map { it.name }).containsExactly("pswitch_0", "pswitch_1").inOrder()
        assertThat(payload.labels).containsExactly("pswitch_data_0")
    }

    @Test
    fun `parser handles sparse switch, array data, annotations and params`() {
        val code = classWith(
            """
            .param p0, "x"
            .annotation runtime Ljavax/annotation/Nullable;
            .end annotation
            .end param
            sparse-switch p0, :sw
            new-array v0, v1, [I
            fill-array-data v0, :arr
            return-void
            :sw
            .sparse-switch
                0x5 -> :a
                0x64 -> :a
            .end sparse-switch
            :arr
            .array-data 4
                0x1 0x2
                0x3
            .end array-data
            :a
            """,
            signature = "test(I)V",
        )
        val result = SmaliParser.parse(code)
        assertThat(result.diagnostics.map { it.code }).doesNotContain(DiagnosticCode.SYNTAX)
        val m = result.file.methods.single()
        val payloads = m.items.filterIsInstance<PayloadDirective>()
        assertThat(payloads[0].keys).containsExactly(5L, 100L).inOrder()
        assertThat(payloads[1].elementWidth).isEqualTo(4)
        assertThat(payloads[1].values).containsExactly("0x1", "0x2", "0x3").inOrder()
    }

    @Test
    fun `literal parsing understands suffixes, chars, floats and octal`() {
        val r = com.riyadm.smalihelperplus.core.smali.ast.SourceRange(1, 0, 0)
        assertThat(SmaliParser.parseLiteral("0x10L", r)!!.type).isEqualTo(LiteralType.LONG)
        assertThat(SmaliParser.parseLiteral("0x10L", r)!!.value).isEqualTo(BigInteger.valueOf(16))
        assertThat(SmaliParser.parseLiteral("-0x1t", r)!!.value).isEqualTo(BigInteger.valueOf(-1))
        assertThat(SmaliParser.parseLiteral("'a'", r)!!.value).isEqualTo(BigInteger.valueOf(97))
        assertThat(SmaliParser.parseLiteral("1.5f", r)!!.type).isEqualTo(LiteralType.FLOAT)
        assertThat(SmaliParser.parseLiteral("010", r)!!.value).isEqualTo(BigInteger.valueOf(8))
        assertThat(SmaliParser.parseLiteral("NaN", r)!!.floatValue!!.isNaN()).isTrue()
    }

    @Test
    fun `lenient mode turns bare instructions into an implicit method`() {
        val result = SmaliParser.parse(SmaliSamples.SNIPPET, ParseMode.LENIENT)
        assertThat(result.file.isFragment).isTrue()
        assertThat(result.file.methods.single().isImplicit).isTrue()
        assertThat(result.file.methods.single().instructions).hasSize(4)
        assertThat(result.diagnostics).isEmpty()
        val strict = SmaliParser.parse(SmaliSamples.SNIPPET, ParseMode.FILE)
        assertThat(strict.diagnostics.map { it.code }).contains(DiagnosticCode.OUTSIDE_METHOD)
    }
}
