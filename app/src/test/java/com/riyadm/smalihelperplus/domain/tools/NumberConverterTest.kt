package com.riyadm.smalihelperplus.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigInteger

class NumberConverterTest {

    private fun int(text: String, rule: OctalRule = OctalRule.LEADING_ZERO): ParsedInteger {
        val p = NumberConverter.parse(text, rule)
        assertThat(p).isInstanceOf(NumberParse.Integer::class.java)
        return (p as NumberParse.Integer).parsed
    }

    @Test fun parsesDecimalHexBinaryOctal() {
        assertThat(int("42").value).isEqualTo(BigInteger.valueOf(42))
        assertThat(int("0x2A").value).isEqualTo(BigInteger.valueOf(42))
        assertThat(int("0b101010").value).isEqualTo(BigInteger.valueOf(42))
        assertThat(int("0o52").value).isEqualTo(BigInteger.valueOf(42))
        assertThat(int("052").value).isEqualTo(BigInteger.valueOf(42))
    }

    @Test fun negativeLiterals() {
        assertThat(int("-0x1").value).isEqualTo(BigInteger.valueOf(-1))
        assertThat(int("-42").value).isEqualTo(BigInteger.valueOf(-42))
        assertThat(int("+7").value).isEqualTo(BigInteger.valueOf(7))
        assertThat(int("-0b11").value).isEqualTo(BigInteger.valueOf(-3))
    }

    @Test fun octalRuleOption() {
        assertThat(int("010", OctalRule.LEADING_ZERO).value).isEqualTo(BigInteger.valueOf(8))
        assertThat(int("010", OctalRule.PREFIX_ONLY).value).isEqualTo(BigInteger.valueOf(10))
        assertThat(int("0", OctalRule.LEADING_ZERO).value).isEqualTo(BigInteger.ZERO)
        assertThat(int("0o10", OctalRule.PREFIX_ONLY).value).isEqualTo(BigInteger.valueOf(8))
    }

    @Test fun suffixes() {
        assertThat(int("0x7fL").suffix).isEqualTo(IntSuffix.LONG)
        assertThat(int("0x7ft").suffix).isEqualTo(IntSuffix.BYTE)
        assertThat(int("-1s").suffix).isEqualTo(IntSuffix.SHORT)
        assertThat(int("0x7fL").value).isEqualTo(BigInteger.valueOf(127))
        assertThat(int("100l").suffix).isEqualTo(IntSuffix.LONG)
    }

    @Test fun underscoresAllowed() {
        assertThat(int("1_000").value).isEqualTo(BigInteger.valueOf(1000))
        assertThat(int("0xFF_FF").value).isEqualTo(BigInteger.valueOf(65535))
    }

    @Test fun invalidInputsGiveErrors() {
        assertThat(NumberConverter.parse("")).isInstanceOf(NumberParse.Error::class.java)
        assertThat(NumberConverter.parse("0x")).isInstanceOf(NumberParse.Error::class.java)
        assertThat(NumberConverter.parse("0xZZ")).isInstanceOf(NumberParse.Error::class.java)
        assertThat(NumberConverter.parse("0b102")).isInstanceOf(NumberParse.Error::class.java)
        assertThat(NumberConverter.parse("12abc")).isInstanceOf(NumberParse.Error::class.java)
        assertThat(NumberConverter.parse("-")).isInstanceOf(NumberParse.Error::class.java)
        assertThat(NumberConverter.parse("0xFFFFFFFFFFFFFFFFF")).isInstanceOf(NumberParse.Error::class.java)
        val e = NumberConverter.parse("0b102") as NumberParse.Error
        assertThat(e.position).isEqualTo(4)
    }

    @Test fun signedAndUnsignedViews() {
        val v8 = NumberConverter.view(BigInteger.valueOf(200), 8)
        assertThat(v8.fits).isTrue()
        assertThat(v8.signed).isEqualTo(BigInteger.valueOf(-56))
        assertThat(v8.unsigned).isEqualTo(BigInteger.valueOf(200))
        assertThat(v8.hex).isEqualTo("0xc8")

        val m1 = NumberConverter.view(BigInteger.valueOf(-1), 32)
        assertThat(m1.unsigned).isEqualTo(BigInteger.valueOf(4294967295L))
        assertThat(m1.hex).isEqualTo("0xffffffff")
        assertThat(m1.smali).isEqualTo("-0x1")

        val tooBig = NumberConverter.view(BigInteger.valueOf(300), 8)
        assertThat(tooBig.fits).isFalse()
        assertThat(tooBig.unsigned).isEqualTo(BigInteger.valueOf(44))

        val l = NumberConverter.view(BigInteger.valueOf(-1), 64)
        assertThat(l.hex).isEqualTo("0xffffffffffffffff")
        assertThat(l.smali).isEqualTo("-0x1L")
        assertThat(l.unsigned).isEqualTo(BigInteger("18446744073709551615"))
    }

    @Test fun viewFormats() {
        val v = NumberConverter.view(BigInteger.valueOf(5), 8)
        assertThat(v.binary).isEqualTo("0b0000_0101")
        assertThat(v.octal).isEqualTo("0o5")
        assertThat(v.smali).isEqualTo("0x5t")
        assertThat(NumberConverter.view(BigInteger.valueOf(-2), 16).smali).isEqualTo("-0x2s")
    }

    @Test fun floatBitPatterns() {
        assertThat(NumberConverter.floatToBits(10.0f)).isEqualTo(0x41200000)
        assertThat(NumberConverter.floatToBits(1.0f)).isEqualTo(0x3f800000)
        assertThat(NumberConverter.floatToBits(-0.0f)).isEqualTo(0x80000000.toInt())
        assertThat(NumberConverter.bitsToFloat(0x40490fdb)).isWithin(1e-6f).of(3.1415927f)
        assertThat(NumberConverter.doubleToBits(1.0)).isEqualTo(0x3ff0000000000000L)
        assertThat(NumberConverter.bitsToDouble(0x4024000000000000L)).isEqualTo(10.0)
    }

    @Test fun bitPatternReadAsFloat() {
        val v = NumberConverter.view(BigInteger.valueOf(0x41200000L), 32)
        assertThat(v.asFloatingText).isEqualTo("10.0f")
        assertThat(NumberConverter.view(BigInteger.ONE, 8).asFloatingText).isNull()
    }

    @Test fun floatDetailsClassify() {
        assertThat(NumberConverter.floatDetails(0x7f800000).classification).isEqualTo("infinity")
        assertThat(NumberConverter.floatDetails(0x7fc00000).classification).isEqualTo("NaN")
        assertThat(NumberConverter.floatDetails(0).classification).isEqualTo("zero")
        assertThat(NumberConverter.floatDetails(1).classification).isEqualTo("subnormal")
        val d = NumberConverter.floatDetails(0x41200000)
        assertThat(d.classification).isEqualTo("normal")
        assertThat(d.exponentUnbiased).isEqualTo(3)
        assertThat(d.sign).isEqualTo(0)
        assertThat(NumberConverter.doubleDetails(0xbff0000000000000UL.toLong()).sign).isEqualTo(1)
    }

    @Test fun constRangesNarrow() {
        fun fitting(v: Long) = NumberConverter.constOptions(v, wide = false).filter { it.fits }.map { it.opcode }
        assertThat(fitting(7)).containsExactly("const/4", "const/16", "const").inOrder()
        assertThat(fitting(8)).containsExactly("const/16", "const").inOrder()
        assertThat(fitting(-8)).containsExactly("const/4", "const/16", "const").inOrder()
        assertThat(fitting(-9)).containsExactly("const/16", "const").inOrder()
        assertThat(fitting(32767)).containsExactly("const/16", "const").inOrder()
        assertThat(fitting(32768)).containsExactly("const").inOrder()
        assertThat(fitting(0x00010000)).containsExactly("const/high16", "const").inOrder()
        assertThat(fitting(0x41200000)).containsExactly("const/high16", "const").inOrder()
    }

    @Test fun constRecommendationPicksSmallest() {
        fun best(v: Long, wide: Boolean = false) = NumberConverter.constOptions(v, wide).single { it.recommended }.opcode
        assertThat(best(1)).isEqualTo("const/4")
        assertThat(best(1000)).isEqualTo("const/16")
        assertThat(best(0x41200000)).isEqualTo("const/high16")
        assertThat(best(0x12345678)).isEqualTo("const")
        assertThat(best(1, true)).isEqualTo("const-wide/16")
        assertThat(best(0x100000000L, true)).isEqualTo("const-wide")
        assertThat(best(0x4024000000000000L, true)).isEqualTo("const-wide/high16")
        assertThat(best(100000, true)).isEqualTo("const-wide/32")
    }

    @Test fun constWideRanges() {
        fun fitting(v: Long) = NumberConverter.constOptions(v, wide = true).filter { it.fits }.map { it.opcode }
        assertThat(fitting(-32768)).containsExactly("const-wide/16", "const-wide/32", "const-wide").inOrder()
        assertThat(fitting(32768)).containsExactly("const-wide/32", "const-wide").inOrder()
        assertThat(fitting(Int.MAX_VALUE.toLong())).containsExactly("const-wide/32", "const-wide").inOrder()
        assertThat(fitting(Int.MAX_VALUE.toLong() + 1)).containsExactly("const-wide").inOrder()
        assertThat(fitting(0x0001000000000000L)).containsExactly("const-wide/high16", "const-wide").inOrder()
        assertThat(fitting(0x0000800000000000L)).containsExactly("const-wide").inOrder()
    }

    @Test fun generatedSmaliLines() {
        val opts = NumberConverter.constOptions(0x41200000L, wide = false, register = "v0", comment = "10.0f")
        assertThat(opts.single { it.opcode == "const/high16" }.smali).isEqualTo("const/high16 v0, 0x41200000  # 10.0f")
        assertThat(NumberConverter.constOptions(-1, false, "v3").first().smali).isEqualTo("const/4 v3, -0x1")
        assertThat(NumberConverter.constOptions(5, true, "v2").first().smali).isEqualTo("const-wide/16 v2, 0x5L")
        assertThat(NumberConverter.constOptions(Long.MIN_VALUE, true).last().smali).isEqualTo("const-wide v0, -0x8000000000000000L")
        assertThat(NumberConverter.constOptions(100, false).single { it.opcode == "const/4" }.smali).isNull()
    }

    @Test fun analyzeIntegerChoosesWidth() {
        val a = (NumberConverter.analyze("0x7f") as NumberAnalysis.IntegerResult).analysis
        assertThat(a.primaryWidth).isEqualTo(32)
        val b = (NumberConverter.analyze("0x7fL") as NumberAnalysis.IntegerResult).analysis
        assertThat(b.primaryWidth).isEqualTo(64)
        assertThat(b.constOptions.first { it.recommended }.opcode).isEqualTo("const-wide/16")
        val c = (NumberConverter.analyze("0x100000000") as NumberAnalysis.IntegerResult).analysis
        assertThat(c.primaryWidth).isEqualTo(64)
        assertThat(c.notes.joinToString()).contains("'L'")
        val d = (NumberConverter.analyze("-5t") as NumberAnalysis.IntegerResult).analysis
        assertThat(d.primaryWidth).isEqualTo(8)
        assertThat(d.constOptions.first { it.recommended }.opcode).isEqualTo("const/4")
    }

    @Test fun unsigned32BitHexWrapsToSigned() {
        val a = (NumberConverter.analyze("0xFFFFFFFF") as NumberAnalysis.IntegerResult).analysis
        assertThat(a.primaryWidth).isEqualTo(32)
        assertThat(a.primary.signed).isEqualTo(BigInteger.valueOf(-1))
        assertThat(a.constOptions.first { it.recommended }.smali).isEqualTo("const/4 v0, -0x1")
    }

    @Test fun parsesFloatingLiterals() {
        val a = (NumberConverter.analyze("10.0f") as NumberAnalysis.FloatingResult).analysis
        assertThat(a.float.hex).isEqualTo("0x41200000")
        assertThat(a.preferDouble).isFalse()
        assertThat(a.floatConstOptions.first { it.recommended }.smali).isEqualTo("const/high16 v0, 0x41200000  # 10.0f")
        assertThat(a.double.hex).isEqualTo("0x4024000000000000")
        assertThat(a.doubleConstOptions.first { it.recommended }.opcode).isEqualTo("const-wide/high16")

        val b = (NumberConverter.analyze("-1.5") as NumberAnalysis.FloatingResult).analysis
        assertThat(b.parsed.floatValue).isEqualTo(-1.5f)
        assertThat(b.preferDouble).isTrue()
        assertThat(b.float.sign).isEqualTo(1)

        val c = (NumberConverter.analyze("1e3") as NumberAnalysis.FloatingResult).analysis
        assertThat(c.parsed.doubleValue).isEqualTo(1000.0)
    }

    @Test fun specialFloatingNames() {
        val nan = (NumberConverter.analyze("NaN") as NumberAnalysis.FloatingResult).analysis
        assertThat(nan.float.classification).isEqualTo("NaN")
        val inf = (NumberConverter.analyze("-Infinity") as NumberAnalysis.FloatingResult).analysis
        assertThat(inf.float.hex).isEqualTo("0xff800000")
    }

    @Test fun hexLiteralFormatting() {
        assertThat(NumberConverter.hexLiteral(255)).isEqualTo("0xff")
        assertThat(NumberConverter.hexLiteral(-255, "L")).isEqualTo("-0xffL")
        assertThat(NumberConverter.hexLiteral(0)).isEqualTo("0x0")
    }

    @Test fun failureForGarbage() {
        val r = NumberConverter.analyze("hello")
        assertThat(r).isInstanceOf(NumberAnalysis.Failure::class.java)
    }
}
