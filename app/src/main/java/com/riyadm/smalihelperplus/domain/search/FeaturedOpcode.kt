package com.riyadm.smalihelperplus.domain.search

import com.riyadm.smalihelperplus.domain.model.Opcode

/** Deterministic "opcode of the day". */
object FeaturedOpcode {

    /**
     * Picks the featured opcode for [epochDay]. The same input list and day always give the same opcode, consecutive
     * days give different ones, and every eligible opcode is shown once per cycle (cycle length = number of opcodes).
     * Payload pseudo-instructions and `nop` are skipped unless nothing else exists.
     */
    fun pick(opcodes: List<Opcode>, epochDay: Long): Opcode? {
        if (opcodes.isEmpty()) return null
        val eligible = opcodes.filter { !it.name.endsWith("-payload") && it.name != "nop" }.ifEmpty { opcodes }
        val ordered = eligible.sortedWith(compareBy<Opcode> { mix(it.name) }.thenBy { it.name })
        return ordered[Math.floorMod(epochDay, ordered.size.toLong()).toInt()]
    }

    /** Stable string hash (does not rely on platform hashing) so the rotation order is identical on every device. */
    private fun mix(name: String): Long {
        var h = 1125899906842597L
        for (c in name) h = 31 * h + c.code
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 29)
        return h
    }
}
