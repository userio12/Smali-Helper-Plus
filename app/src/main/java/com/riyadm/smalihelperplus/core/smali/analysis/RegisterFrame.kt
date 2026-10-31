package com.riyadm.smalihelperplus.core.smali.analysis

import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.ParamDirective
import com.riyadm.smalihelperplus.core.smali.ast.RegisterOperand
import com.riyadm.smalihelperplus.core.smali.model.Descriptors

/**
 * Canonical register identity. When the method's frame is known every register is expressed as `vN`
 * (parameter registers `pN` become `v(locals + N)`); otherwise `pN` registers keep their own namespace.
 */
data class RegKey(val prefix: Char, val number: Int) : Comparable<RegKey> {
    override fun toString(): String = "$prefix$number"
    fun next(): RegKey = RegKey(prefix, number + 1)
    override fun compareTo(other: RegKey): Int =
        compareValuesBy(this, other, { it.prefix != 'v' }, { it.number })
}

/** One incoming argument word. [pRegister] is `pN`, [vRegister] the aliased `vN` when the frame is known. */
data class ParameterSlot(
    val pRegister: String,
    val vRegister: String?,
    /** Descriptor of the argument (the declaring class for `this`). */
    val type: String,
    val isThis: Boolean,
    /** True for the second (high) word of a long/double argument. */
    val isHighWord: Boolean,
    /** Index of the declared parameter (0-based), -1 for `this`. */
    val parameterIndex: Int,
    /** Name from a `.param` directive, if present. */
    val name: String?,
) {
    val javaType: String get() = Descriptors.toJavaName(type, simple = true)
}

/** Register frame of a method: `.registers`/`.locals` and the parameter layout. */
class RegisterFrame(val method: MethodDefinition, val declaringClass: String?) {
    /** Total registers, or null when unknown (fragments or missing directives). */
    val size: Int? = if (method.isImplicit) method.registers?.value else method.frameSize

    /** Number of local registers (v0 until the first parameter register), or null when unknown. */
    val locals: Int? = if (method.isImplicit) null else method.localCount?.takeIf { it >= 0 }

    val parameterWords: Int? = if (method.isImplicit) null else method.parameterWords

    val isKnown: Boolean get() = size != null && locals != null && parameterWords != null

    /** Frame index of a register (`vN` → N, `pN` → locals + N), or null when it cannot be determined. */
    fun index(reg: RegisterOperand): Int? = when {
        reg.number == Int.MAX_VALUE -> null
        reg.isParameter -> locals?.let { it + reg.number }
        else -> reg.number
    }

    fun key(reg: RegisterOperand): RegKey {
        val idx = index(reg)
        return if (idx != null && (isKnown || !reg.isParameter)) RegKey('v', idx) else RegKey(reg.prefix, reg.number)
    }

    /** Human readable name of a key, e.g. `v5 (p1)` for an aliased parameter register. */
    fun display(key: RegKey): String {
        val l = locals
        return if (key.prefix == 'v' && isKnown && l != null && key.number >= l) "v${key.number} (p${key.number - l})" else key.toString()
    }

    /** Preferred short name for pseudocode: parameters are shown as `pN` (or `this`). */
    fun preferredName(key: RegKey): String {
        val l = locals
        if (key.prefix == 'v' && isKnown && l != null && key.number >= l) {
            val p = key.number - l
            val slot = parameterSlots.getOrNull(p)
            if (slot != null && slot.isThis) return "this"
            return slot?.name?.takeIf { isJavaIdentifier(it) } ?: "p$p"
        }
        if (key.prefix == 'p' && key.number == 0 && !method.isStatic && !method.isImplicit) return "this"
        return key.toString()
    }

    val parameterSlots: List<ParameterSlot> by lazy(LazyThreadSafetyMode.NONE) { buildSlots() }

    private fun buildSlots(): List<ParameterSlot> {
        val proto = method.proto ?: return emptyList()
        if (method.isImplicit) return emptyList()
        val names = HashMap<Int, String>()
        method.items.filterIsInstance<ParamDirective>().forEach { p ->
            val r = p.register ?: return@forEach
            if (r.isParameter && p.name != null) names[r.number] = p.name
        }
        val out = ArrayList<ParameterSlot>()
        var p = 0
        fun v(pn: Int): String? = locals?.let { "v${it + pn}" }
        if (!method.isStatic) {
            out += ParameterSlot("p0", v(0), declaringClass ?: "Ljava/lang/Object;", true, false, -1, "this")
            p = 1
        }
        proto.parameters.forEachIndexed { i, type ->
            out += ParameterSlot("p$p", v(p), type, false, false, i, names[p])
            if (Descriptors.isWide(type)) {
                out += ParameterSlot("p${p + 1}", v(p + 1), type, false, true, i, names[p])
                p += 2
            } else {
                p += 1
            }
        }
        return out
    }

    private fun isJavaIdentifier(s: String) = s.isNotEmpty() && (s[0].isLetter() || s[0] == '_') && s.all { it.isLetterOrDigit() || it == '_' }
}
