package com.riyadm.smalihelperplus.domain.tools

import java.math.BigInteger

/** How a leading zero is interpreted in an integer literal such as `017`. */
enum class OctalRule(val label: String) {
    /** Only `0o17` is octal; `017` is decimal 17. */
    PREFIX_ONLY("Octal only with 0o prefix"),

    /** Java / smali rule: `017` is octal 15 (and `0o17` also works). */
    LEADING_ZERO("Leading 0 means octal (smali rule)"),
}

/** Smali integer literal suffixes. */
enum class IntSuffix(val char: Char, val width: Int) {
    BYTE('t', 8), SHORT('s', 16), LONG('L', 64)
}

/** Parsed integer literal. [value] is the exact mathematical value (sign applied). */
data class ParsedInteger(
    val value: BigInteger,
    val radix: Int,
    val suffix: IntSuffix?,
)

/** Parsed floating point literal. */
data class ParsedFloating(
    val floatValue: Float,
    val doubleValue: Double,
    /** True when written with an `f` suffix, false for `d` suffix, null when no suffix. */
    val explicitFloat: Boolean?,
)

sealed interface NumberParse {
    data class Integer(val parsed: ParsedInteger) : NumberParse
    data class Floating(val parsed: ParsedFloating) : NumberParse
    data class Error(val message: String, val position: Int) : NumberParse
}

/** The same bits viewed at one bit width. */
data class NumberView(
    val width: Int,
    /** False if the value does not fit [width] bits as signed or unsigned; the views then show the truncated bits. */
    val fits: Boolean,
    val signed: BigInteger,
    val unsigned: BigInteger,
    /** e.g. "0xff" padded to the width. */
    val hex: String,
    /** e.g. "0b1111_1111". */
    val binary: String,
    val octal: String,
    /** Smali literal with the width's suffix, e.g. "-0x1t", "0x7fffffffffffffffL". */
    val smali: String,
    /** The bit pattern read as IEEE 754 (only for widths 32 and 64). */
    val asFloatingText: String?,
)

/** Breakdown of an IEEE 754 bit pattern. */
data class FloatingDetails(
    val bits: Int,
    val sign: Int,
    val exponentRaw: Long,
    val exponentUnbiased: Int?,
    val mantissaBits: Long,
    val classification: String,
    val hex: String,
    val text: String,
)

/** One `const*` instruction candidate for a value. */
data class ConstOption(
    val opcode: String,
    val fits: Boolean,
    /** Human readable rule, e.g. "-8 to 7". */
    val rule: String,
    val codeUnits: Int,
    /** Ready-to-paste smali line, or null when the value does not fit. */
    val smali: String?,
    val recommended: Boolean,
)

data class IntegerAnalysis(
    val parsed: ParsedInteger,
    /** Width used for the const suggestions: 8/16/32 use the 32-bit const family, 64 the wide family. */
    val primaryWidth: Int,
    val views: List<NumberView>,
    val constOptions: List<ConstOption>,
    val notes: List<String>,
) {
    val primary: NumberView get() = views.first { it.width == primaryWidth }
}

data class FloatingAnalysis(
    val parsed: ParsedFloating,
    val float: FloatingDetails,
    val double: FloatingDetails,
    val floatConstOptions: List<ConstOption>,
    val doubleConstOptions: List<ConstOption>,
    val preferDouble: Boolean,
    val notes: List<String>,
)

sealed interface NumberAnalysis {
    data class IntegerResult(val analysis: IntegerAnalysis) : NumberAnalysis
    data class FloatingResult(val analysis: FloatingAnalysis) : NumberAnalysis
    data class Failure(val message: String, val position: Int) : NumberAnalysis
}

/** Parses smali-style numeric literals and shows them in every base, width and IEEE 754 form. */
object NumberConverter {
    val WIDTHS = listOf(8, 16, 32, 64)

    private val FLOAT_REGEX = Regex("^(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?[fFdD]?$")

    // ---------------------------------------------------------------- parsing

    fun parse(input: String, octalRule: OctalRule = OctalRule.LEADING_ZERO): NumberParse {
        val lead = input.length - input.trimStart().length
        var text = input.trim()
        if (text.isEmpty()) return NumberParse.Error("Enter a number", 0)
        var pos = lead
        var negative = false
        if (text[0] == '-' || text[0] == '+') {
            negative = text[0] == '-'
            text = text.substring(1)
            pos++
            if (text.isEmpty()) return NumberParse.Error("A sign must be followed by digits", pos)
        }
        // Special floating point names.
        val special = parseSpecialFloating(text, negative)
        if (special != null) return NumberParse.Floating(special)

        val lower = text.lowercase()
        val radix: Int
        var bodyStart: Int
        when {
            lower.startsWith("0x") -> { radix = 16; bodyStart = 2 }
            lower.startsWith("0b") -> { radix = 2; bodyStart = 2 }
            lower.startsWith("0o") -> { radix = 8; bodyStart = 2 }
            else -> {
                // Plain decimal / float / leading-zero octal.
                if (FLOAT_REGEX.matches(text) && (text.any { it == '.' || it == 'e' || it == 'E' } || text.last() in "fFdD")) {
                    return parseFloating(text, negative, pos)
                }
                val digitsOnly = text.trimEnd('L', 'l', 't', 'T', 's', 'S')
                if (octalRule == OctalRule.LEADING_ZERO && digitsOnly.length > 1 && digitsOnly[0] == '0' &&
                    digitsOnly.all { it.isDigit() || it == '_' }
                ) {
                    radix = 8; bodyStart = 1
                } else {
                    radix = 10; bodyStart = 0
                }
            }
        }
        var body = text.substring(bodyStart)
        var suffix: IntSuffix? = null
        if (body.isNotEmpty()) {
            when (body.last()) {
                'L', 'l' -> suffix = IntSuffix.LONG
                't', 'T' -> suffix = IntSuffix.BYTE
                's', 'S' -> suffix = IntSuffix.SHORT
            }
            if (suffix != null) body = body.dropLast(1)
        }
        if (body.startsWith("_") || body.endsWith("_")) {
            return NumberParse.Error("Underscores may only appear between digits", pos + bodyStart)
        }
        val digits = body.replace("_", "")
        if (digits.isEmpty()) {
            val what = when (radix) { 16 -> "hex"; 2 -> "binary"; 8 -> "octal"; else -> "decimal" }
            return NumberParse.Error("No $what digits found", pos + text.length)
        }
        for ((i, ch) in body.withIndex()) {
            if (ch == '_') continue
            if (Character.digit(ch, radix) < 0) {
                val radixName = when (radix) { 16 -> "hex"; 2 -> "binary"; 8 -> "octal"; else -> "decimal" }
                return NumberParse.Error("'$ch' is not a valid $radixName digit", pos + bodyStart + i)
            }
        }
        var value = BigInteger(digits, radix)
        if (negative) value = value.negate()
        val limit = BigInteger.ONE.shiftLeft(64)
        if (value >= limit || value < limit.negate().shiftRight(1)) {
            return NumberParse.Error("Value does not fit in 64 bits", pos)
        }
        return NumberParse.Integer(ParsedInteger(value, radix, suffix))
    }

    private fun parseSpecialFloating(text: String, negative: Boolean): ParsedFloating? {
        var t = text
        var explicit: Boolean? = null
        val lower = t.lowercase()
        val base = when {
            lower == "nan" || lower == "nanf" || lower == "nand" -> "nan"
            lower == "infinity" || lower == "inf" || lower == "infinityf" || lower == "infinityd" -> "inf"
            else -> return null
        }
        if (lower.endsWith("f") && base == "nan" && lower.length == 4) explicit = true
        if (lower.endsWith("d") && base == "nan" && lower.length == 4) explicit = false
        if (lower == "infinityf") explicit = true
        if (lower == "infinityd") explicit = false
        t = base
        return if (t == "nan") {
            ParsedFloating(Float.NaN, Double.NaN, explicit)
        } else {
            val d = if (negative) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            ParsedFloating(d.toFloat(), d, explicit)
        }
    }

    private fun parseFloating(text: String, negative: Boolean, pos: Int): NumberParse {
        var body = text
        var explicit: Boolean? = null
        when (body.last()) {
            'f', 'F' -> { explicit = true; body = body.dropLast(1) }
            'd', 'D' -> { explicit = false; body = body.dropLast(1) }
        }
        val d = body.toDoubleOrNull() ?: return NumberParse.Error("Not a valid floating point number", pos)
        val f = body.toFloatOrNull() ?: return NumberParse.Error("Not a valid floating point number", pos)
        val sd = if (negative) -d else d
        val sf = if (negative) -f else f
        return NumberParse.Floating(ParsedFloating(sf, sd, explicit))
    }

    // ---------------------------------------------------------------- analysis

    fun analyze(input: String, octalRule: OctalRule = OctalRule.LEADING_ZERO, register: String = "v0"): NumberAnalysis =
        when (val p = parse(input, octalRule)) {
            is NumberParse.Error -> NumberAnalysis.Failure(p.message, p.position)
            is NumberParse.Integer -> NumberAnalysis.IntegerResult(analyzeInteger(p.parsed, register))
            is NumberParse.Floating -> NumberAnalysis.FloatingResult(analyzeFloating(p.parsed, register))
        }

    fun analyzeInteger(parsed: ParsedInteger, register: String = "v0"): IntegerAnalysis {
        val value = parsed.value
        val views = WIDTHS.map { view(value, it) }
        val fits32 = view(value, 32).fits
        val primaryWidth = when {
            parsed.suffix != null -> parsed.suffix.width
            fits32 -> 32
            else -> 64
        }
        val notes = ArrayList<String>()
        if (parsed.suffix == null && !fits32) {
            notes += "This value needs 64 bits; smali requires an 'L' suffix on long literals."
        }
        if (parsed.suffix == IntSuffix.BYTE) notes += "Byte literals ('t') are sign-extended to 32 bits when used with const."
        if (parsed.suffix == IntSuffix.SHORT) notes += "Short literals ('s') are sign-extended to 32 bits when used with const."
        if (parsed.suffix != null && !views.first { it.width == primaryWidth }.fits) {
            notes += "The value does not fit in ${primaryWidth} bits, so the ${parsed.suffix.char} suffix would truncate it."
        }
        val wide = primaryWidth == 64
        val constValue = if (wide) view(value, 64).signed.toLong() else view(value, 32).signed.toLong()
        val options = constOptions(constValue, wide, register)
        return IntegerAnalysis(parsed, primaryWidth, views, options, notes)
    }

    fun analyzeFloating(parsed: ParsedFloating, register: String = "v0"): FloatingAnalysis {
        val fBits = parsed.floatValue.toRawBits()
        val dBits = parsed.doubleValue.toRawBits()
        val floatDetails = floatDetails(fBits)
        val doubleDetails = doubleDetails(dBits)
        val floatLiteral = formatFloatLiteral(parsed.floatValue)
        val doubleLiteral = formatDoubleLiteral(parsed.doubleValue)
        val notes = ArrayList<String>()
        notes += "smali writes float literals with an 'f' suffix (1.0f) and double literals without one (1.0)."
        if (parsed.floatValue.toDouble() != parsed.doubleValue && !parsed.doubleValue.isNaN() && !parsed.floatValue.isInfinite()) {
            notes += "This number is not exactly representable as a float; the float pattern holds ${parsed.floatValue} (rounded)."
        }
        if (parsed.floatValue.isInfinite() && !parsed.doubleValue.isInfinite()) {
            notes += "The value overflows a float (it becomes Infinity) but fits a double."
        }
        return FloatingAnalysis(
            parsed = parsed,
            float = floatDetails,
            double = doubleDetails,
            floatConstOptions = constOptions(fBits.toLong(), wide = false, register = register, comment = floatLiteral),
            doubleConstOptions = constOptions(dBits, wide = true, register = register, comment = doubleLiteral),
            preferDouble = parsed.explicitFloat != true,
            notes = notes,
        )
    }

    fun view(value: BigInteger, width: Int): NumberView {
        require(width in WIDTHS) { "Unsupported width $width" }
        val modulus = BigInteger.ONE.shiftLeft(width)
        val unsigned = value.mod(modulus)
        val half = BigInteger.ONE.shiftLeft(width - 1)
        val signed = if (unsigned >= half) unsigned - modulus else unsigned
        val fits = value >= half.negate() && value < modulus
        val hexDigits = unsigned.toString(16).padStart(width / 4, '0')
        val binDigits = unsigned.toString(2).padStart(width, '0').chunked(4).joinToString("_")
        val suffix = when (width) { 8 -> "t"; 16 -> "s"; 64 -> "L"; else -> "" }
        val smali = (if (signed.signum() < 0) "-0x" + signed.negate().toString(16) else "0x" + signed.toString(16)) + suffix
        val floating = when (width) {
            32 -> formatFloatLiteral(Float.fromBits(unsigned.toInt()))
            64 -> formatDoubleLiteral(Double.fromBits(unsigned.toLong()))
            else -> null
        }
        return NumberView(
            width = width, fits = fits, signed = signed, unsigned = unsigned,
            hex = "0x$hexDigits", binary = "0b$binDigits", octal = "0o" + unsigned.toString(8),
            smali = smali, asFloatingText = floating,
        )
    }

    // ---------------------------------------------------------------- IEEE 754

    fun floatToBits(value: Float): Int = value.toRawBits()
    fun doubleToBits(value: Double): Long = value.toRawBits()
    fun bitsToFloat(bits: Int): Float = Float.fromBits(bits)
    fun bitsToDouble(bits: Long): Double = Double.fromBits(bits)

    fun formatFloatLiteral(f: Float): String = if (f.isNaN() || f.isInfinite()) f.toString() else "${f}f"
    fun formatDoubleLiteral(d: Double): String = d.toString()

    fun floatDetails(bits: Int): FloatingDetails {
        val sign = (bits ushr 31) and 1
        val exp = ((bits ushr 23) and 0xFF).toLong()
        val mant = (bits and 0x7FFFFF).toLong()
        val cls = classify(exp, mant, 0xFF)
        return FloatingDetails(
            bits = 32, sign = sign, exponentRaw = exp,
            exponentUnbiased = if (exp in 1..0xFE) (exp - 127).toInt() else if (exp == 0L && mant != 0L) -126 else null,
            mantissaBits = mant, classification = cls,
            hex = "0x" + Integer.toHexString(bits).padStart(8, '0'),
            text = formatFloatLiteral(Float.fromBits(bits)),
        )
    }

    fun doubleDetails(bits: Long): FloatingDetails {
        val sign = ((bits ushr 63) and 1L).toInt()
        val exp = (bits ushr 52) and 0x7FF
        val mant = bits and 0xFFFFFFFFFFFFFL
        val cls = classify(exp, mant, 0x7FF)
        return FloatingDetails(
            bits = 64, sign = sign, exponentRaw = exp,
            exponentUnbiased = if (exp in 1..0x7FE) (exp - 1023).toInt() else if (exp == 0L && mant != 0L) -1022 else null,
            mantissaBits = mant, classification = cls,
            hex = "0x" + java.lang.Long.toHexString(bits).padStart(16, '0'),
            text = formatDoubleLiteral(Double.fromBits(bits)),
        )
    }

    private fun classify(exp: Long, mant: Long, maxExp: Int): String = when {
        exp == 0L && mant == 0L -> "zero"
        exp == 0L -> "subnormal"
        exp == maxExp.toLong() && mant == 0L -> "infinity"
        exp == maxExp.toLong() -> "NaN"
        else -> "normal"
    }

    // ---------------------------------------------------------------- const instruction helper

    /** Signed hex like `0x1f`, `-0x8`, with an optional suffix. */
    fun hexLiteral(value: Long, suffix: String = ""): String {
        val big = BigInteger.valueOf(value)
        return (if (value < 0) "-0x" + big.negate().toString(16) else "0x" + big.toString(16)) + suffix
    }

    /**
     * Lists every const instruction that can load [value] into [register].
     * For non-wide constants [value] must already be a signed 32-bit value (use [Long.toInt] semantics).
     * The first option with the fewest code units is marked recommended.
     */
    fun constOptions(value: Long, wide: Boolean, register: String = "v0", comment: String? = null): List<ConstOption> {
        val tail = if (comment != null) "  # $comment" else ""
        val raw = ArrayList<ConstOption>()
        if (!wide) {
            val v = value.toInt()
            val vl = v.toLong()
            fun line(op: String, lit: String) = "$op $register, $lit$tail"
            raw += ConstOption("const/4", vl in -8..7, "-8 to 7", 1, if (vl in -8..7) line("const/4", hexLiteral(vl)) else null, false)
            raw += ConstOption("const/16", vl in -32768..32767, "-32768 to 32767", 2, if (vl in -32768..32767) line("const/16", hexLiteral(vl)) else null, false)
            val high = (v and 0xFFFF) == 0
            raw += ConstOption("const/high16", high, "low 16 bits are zero (value = 0xBBBB0000)", 2, if (high) line("const/high16", hexLiteral(vl)) else null, false)
            raw += ConstOption("const", true, "any 32-bit value", 3, line("const", hexLiteral(vl)), false)
        } else {
            fun line(op: String, lit: String) = "$op $register, $lit$tail"
            val fits16 = value in -32768L..32767L
            val fits32 = value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
            val high = (value and 0xFFFFFFFFFFFFL) == 0L
            raw += ConstOption("const-wide/16", fits16, "-32768 to 32767", 2, if (fits16) line("const-wide/16", hexLiteral(value, "L")) else null, false)
            raw += ConstOption("const-wide/32", fits32, "-2147483648 to 2147483647", 3, if (fits32) line("const-wide/32", hexLiteral(value, "L")) else null, false)
            raw += ConstOption("const-wide/high16", high, "low 48 bits are zero (value = 0xBBBB000000000000)", 2, if (high) line("const-wide/high16", hexLiteral(value, "L")) else null, false)
            raw += ConstOption("const-wide", true, "any 64-bit value", 5, line("const-wide", hexLiteral(value, "L")), false)
        }
        val best = raw.filter { it.fits }.minByOrNull { it.codeUnits }
        return raw.map { it.copy(recommended = it === best) }
    }
}
