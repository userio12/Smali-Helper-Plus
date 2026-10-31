package com.riyadm.smalihelperplus.domain.tools

/** How the user declared the register count of a method. */
enum class RegisterDeclaration { LOCALS, REGISTERS }

enum class NoteLevel { INFO, WARNING }

data class RegisterNote(val level: NoteLevel, val text: String)

/** One declared parameter (or `this`) and where it lives. */
data class ParameterSlot(
    /** "this" or the parameter name / "arg1"... */
    val label: String,
    val type: TypeInfo,
    /** Index of the first p-register (p0 = first). */
    val firstP: Int,
    val words: Int,
    /** Index of the first v-register that holds it. */
    val firstV: Int,
    val isThis: Boolean,
) {
    val isWide: Boolean get() = words == 2
    val lastV: Int get() = firstV + words - 1
    val pName: String get() = "p$firstP"
    val vName: String get() = "v$firstV"
    /** True when the first register of this slot cannot be addressed by a 4-bit operand. */
    val beyond4Bit: Boolean get() = lastV > 15
    val beyond8Bit: Boolean get() = lastV > 255
}

/** A single register in the frame. */
data class RegisterRow(
    val v: Int,
    /** p-register index, or null for local registers. */
    val p: Int?,
    /** Short role description, e.g. "this", "arg1 (int)", "low half of long arg2". */
    val role: String,
    val slotLabel: String?,
    val isWideHigh: Boolean,
    val isWideLow: Boolean,
    val reachableBy4Bit: Boolean,
    val reachableBy8Bit: Boolean,
) {
    val vName: String get() = "v$v"
    val pName: String? get() = p?.let { "p$it" }
}

data class RegisterLayout(
    val isStatic: Boolean,
    val proto: MethodProto,
    val slots: List<ParameterSlot>,
    val parameterWords: Int,
    val locals: Int,
    val totalRegisters: Int,
    val rows: List<RegisterRow>,
    val notes: List<RegisterNote>,
) {
    /** Parameter slots whose registers are above v15. */
    val slotsBeyond4Bit: List<ParameterSlot> get() = slots.filter { it.beyond4Bit }
    val slotsBeyond8Bit: List<ParameterSlot> get() = slots.filter { it.beyond8Bit }
    /** The smali directive pair for this layout, e.g. ".locals 3". */
    val localsDirective: String get() = ".locals $locals"
    val registersDirective: String get() = ".registers $totalRegisters"
}

sealed interface RegisterResult {
    data class Success(val layout: RegisterLayout) : RegisterResult
    data class Failure(val message: String, val position: Int = -1) : RegisterResult
}

/** Computes the register frame of a Dalvik method from its signature and `.locals` / `.registers` value. */
object RegisterCalculator {
    const val MAX_REGISTERS = 65535

    /**
     * @param signature a prototype `(IJ)V`, a method reference `LFoo;->m(IJ)V`, a Java signature
     *   `int add(int a, long b)` or a plain parameter list `int a, long b`.
     * @param count the number given to `.locals` or `.registers`.
     */
    fun calculate(
        signature: String,
        isStatic: Boolean,
        declaration: RegisterDeclaration,
        count: Int,
        declaringClass: String? = null,
    ): RegisterResult {
        val parseNotes = mutableListOf<String>()
        val proto = try {
            DescriptorConverter.parseAnySignature(signature, parseNotes)
        } catch (e: DescriptorException) {
            return RegisterResult.Failure(e.message ?: "Invalid signature", e.position)
        }
        return calculate(proto, isStatic, declaration, count, declaringClass, parseNotes)
    }

    fun calculate(
        proto: MethodProto,
        isStatic: Boolean,
        declaration: RegisterDeclaration,
        count: Int,
        declaringClass: String? = null,
        extraNotes: List<String> = emptyList(),
    ): RegisterResult {
        if (count < 0) return RegisterResult.Failure("The register count cannot be negative")
        if (count > MAX_REGISTERS) return RegisterResult.Failure("A method can have at most $MAX_REGISTERS registers")

        val thisWords = if (isStatic) 0 else 1
        val parameterWords = proto.paramWords + thisWords
        val locals: Int
        val total: Int
        when (declaration) {
            RegisterDeclaration.LOCALS -> {
                locals = count
                total = count + parameterWords
            }
            RegisterDeclaration.REGISTERS -> {
                if (count < parameterWords) {
                    return RegisterResult.Failure(
                        ".registers $count is too small: this signature needs $parameterWords register" +
                            "${if (parameterWords == 1) "" else "s"} for its parameters" +
                            if (!isStatic) " (including 'this')" else "",
                    )
                }
                total = count
                locals = count - parameterWords
            }
        }
        if (total > MAX_REGISTERS) {
            return RegisterResult.Failure("Total registers would be $total, but a method can have at most $MAX_REGISTERS")
        }

        val slots = ArrayList<ParameterSlot>()
        var p = 0
        if (!isStatic) {
            val ownerType = declaringClass?.takeIf { it.isNotBlank() }?.let {
                try {
                    DescriptorConverter.parseTypeDescriptor(it, allowVoid = false)
                } catch (e: DescriptorException) {
                    return RegisterResult.Failure("Declaring class: ${e.message}", e.position)
                }
            } ?: TypeInfo("", "the instance (this)", 'L', 0)
            slots += ParameterSlot("this", ownerType, p, 1, locals + p, true)
            p += 1
        }
        proto.params.forEachIndexed { idx, param ->
            val label = param.name ?: "arg${idx + 1}"
            slots += ParameterSlot(label, param.type, p, param.type.words, locals + p, false)
            p += param.type.words
        }

        val rows = ArrayList<RegisterRow>(total.coerceAtMost(4096))
        // Local registers: cap the detailed table to something displayable, but keep correct counts.
        val shownLocals = locals.coerceAtMost(MAX_ROWS)
        for (v in 0 until shownLocals) {
            rows += RegisterRow(v, null, "local register", null, false, false, v <= 15, v <= 255)
        }
        for (slot in slots) {
            for (w in 0 until slot.words) {
                val v = slot.firstV + w
                val role = when {
                    slot.isThis -> "this"
                    slot.isWide && w == 0 -> "${slot.label}: low half of ${slot.type.java}"
                    slot.isWide -> "${slot.label}: high half of ${slot.type.java}"
                    else -> "${slot.label}: ${slot.type.java}"
                }
                rows += RegisterRow(
                    v = v, p = slot.firstP + w, role = role, slotLabel = slot.label,
                    isWideHigh = slot.isWide && w == 1, isWideLow = slot.isWide && w == 0,
                    reachableBy4Bit = v <= 15, reachableBy8Bit = v <= 255,
                )
            }
        }

        val notes = buildNotes(proto, isStatic, declaration, count, locals, total, parameterWords, slots, extraNotes, shownLocals < locals)
        return RegisterResult.Success(RegisterLayout(isStatic, proto, slots, parameterWords, locals, total, rows, notes))
    }

    private const val MAX_ROWS = 300

    private fun buildNotes(
        proto: MethodProto,
        isStatic: Boolean,
        declaration: RegisterDeclaration,
        count: Int,
        locals: Int,
        total: Int,
        parameterWords: Int,
        slots: List<ParameterSlot>,
        extra: List<String>,
        truncated: Boolean,
    ): List<RegisterNote> {
        val n = ArrayList<RegisterNote>()
        extra.distinct().forEach { n += RegisterNote(NoteLevel.INFO, it) }
        n += RegisterNote(
            NoteLevel.INFO,
            "Parameters always occupy the LAST $parameterWords register${if (parameterWords == 1) "" else "s"} " +
                "(v$locals${if (parameterWords > 1) "..v${total - 1}" else ""}); the first $locals are free locals.",
        )
        if (!isStatic) n += RegisterNote(NoteLevel.INFO, "Instance method: p0 is 'this' and is not part of the descriptor.")
        if (slots.any { it.isWide }) {
            n += RegisterNote(
                NoteLevel.INFO,
                "long/double values use two consecutive registers (a pair). Name the first register, e.g. p1 means p1+p2; " +
                    "never overwrite just one half.",
            )
        }
        if (declaration == RegisterDeclaration.REGISTERS) {
            n += RegisterNote(NoteLevel.INFO, ".registers $count with $parameterWords parameter word${if (parameterWords == 1) "" else "s"} leaves $locals local${if (locals == 1) "" else "s"}; .locals $locals is equivalent.")
        } else {
            n += RegisterNote(NoteLevel.INFO, ".locals $locals gives .registers $total (locals + parameter words).")
        }
        val over15 = slots.filter { it.beyond4Bit }
        if (over15.isNotEmpty()) {
            n += RegisterNote(
                NoteLevel.WARNING,
                "${over15.joinToString { it.label + " (" + it.vName + (if (it.isWide) "..v${it.lastV}" else "") + ")" }} " +
                    "sit above v15. 4-bit operand formats (12x, 22c, 22t, 22s, 35c) cannot name them; copy the value into a " +
                    "low register first with move/from16 or move/16 (move-wide/from16, move-object/from16 for wide or object values).",
            )
        }
        val over255 = slots.filter { it.beyond8Bit }
        if (over255.isNotEmpty()) {
            n += RegisterNote(
                NoteLevel.WARNING,
                "${over255.joinToString { it.label + " (" + it.vName + ")" }} sit above v255. 8-bit formats (11x, 21c, 21s, 23x, 22b, ...) " +
                    "cannot name them; only move/16 (32x), move/from16 (22x source) and the /range invokes can read them.",
            )
        }
        if (locals > 16) {
            n += RegisterNote(
                NoteLevel.INFO,
                "With $locals locals every parameter register is above v15. Keep hot values in v0..v15 and treat high registers as spill slots.",
            )
        }
        if (parameterWords > 5) {
            n += RegisterNote(
                NoteLevel.INFO,
                "Calling a method with this signature takes $parameterWords argument words${if (!isStatic) " (including the receiver)" else ""}: " +
                    "plain invoke-* (35c) allows at most 5 argument registers, each v0..v15, so use invoke-*/range (3rc) with consecutive registers.",
            )
        } else if (parameterWords > 0) {
            n += RegisterNote(
                NoteLevel.INFO,
                "Calling it needs $parameterWords argument word${if (parameterWords == 1) "" else "s"}: plain invoke-* allows up to 5, all in v0..v15; otherwise use /range.",
            )
        }
        n += RegisterNote(
            NoteLevel.INFO,
            "Limits: 4-bit operands reach v0..v15, 8-bit operands v0..v255, 16-bit operands (move/16, the source of move/from16, range starts) v0..v65535. " +
                "A method may have at most $MAX_REGISTERS registers.",
        )
        if (total > 255) n += RegisterNote(NoteLevel.WARNING, "Total of $total registers: most instructions only address v0..v255.")
        if (truncated) {
            n += RegisterNote(NoteLevel.INFO, "The table lists only the first $MAX_ROWS local registers; parameters are always shown in full.")
        }
        return n
    }
}
