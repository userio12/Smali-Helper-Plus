package com.riyadm.smalihelperplus.core.smali.analysis

import com.riyadm.smalihelperplus.core.smali.ast.CatchDirective
import com.riyadm.smalihelperplus.core.smali.ast.Instruction
import com.riyadm.smalihelperplus.core.smali.ast.LabelDefinition
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.PayloadDirective

/** What a label points at. */
sealed interface LabelTarget {
    /** Instruction index; equal to the instruction count when the label is at the very end of the method. */
    data class Code(val index: Int) : LabelTarget
    data class Payload(val payload: PayloadDirective) : LabelTarget
}

/** A resolved `.catch` range: instructions [startIndex, endIndex) are covered, the handler starts at [handlerIndex]. */
data class TryRange(
    val exceptionType: String?,
    val startIndex: Int,
    val endIndex: Int,
    val handlerIndex: Int,
    val directive: CatchDirective,
)

/** Resolves labels, payloads and try ranges of a method. */
class MethodLayout(val method: MethodDefinition) {
    val instructions: List<Instruction> = method.instructions
    val labelTargets: Map<String, LabelTarget>
    val labelDefinitions: Map<String, LabelDefinition>
    val duplicateLabels: List<LabelDefinition>
    /** Labels attached to each instruction index. */
    val labelsAt: Map<Int, List<String>>
    val tryRanges: List<TryRange>
    val handlerIndices: Set<Int>
    val payloads: List<PayloadDirective>
    private val boundaries: Map<String, Int>

    init {
        val targets = LinkedHashMap<String, LabelTarget>()
        val defs = LinkedHashMap<String, LabelDefinition>()
        val dups = ArrayList<LabelDefinition>()
        val at = HashMap<Int, MutableList<String>>()
        val pending = ArrayList<String>()
        val boundaryIndices = HashMap<String, Int>()
        val payloadList = ArrayList<PayloadDirective>()
        var insnIndex = 0
        for (item in method.items) {
            when (item) {
                is LabelDefinition -> {
                    if (defs.containsKey(item.name)) dups += item else defs[item.name] = item
                    boundaryIndices.putIfAbsent(item.name, insnIndex)
                    pending += item.name
                }
                is Instruction -> {
                    pending.forEach { targets.putIfAbsent(it, LabelTarget.Code(insnIndex)) }
                    if (pending.isNotEmpty()) at.getOrPut(insnIndex) { ArrayList() }.addAll(pending)
                    pending.clear()
                    insnIndex++
                }
                is PayloadDirective -> {
                    pending.forEach { targets.putIfAbsent(it, LabelTarget.Payload(item)) }
                    pending.clear()
                    payloadList += item
                }
                else -> Unit
            }
        }
        pending.forEach { targets.putIfAbsent(it, LabelTarget.Code(insnIndex)) }
        boundaries = boundaryIndices
        labelTargets = targets
        labelDefinitions = defs
        duplicateLabels = dups
        labelsAt = at
        payloads = payloadList

        val ranges = ArrayList<TryRange>()
        method.items.filterIsInstance<CatchDirective>().forEach { c ->
            val s = (targets[c.start.name] as? LabelTarget.Code)?.index
            val e = boundaries[c.end.name]
            val h = (targets[c.handler.name] as? LabelTarget.Code)?.index
            if (s != null && e != null && h != null) ranges += TryRange(c.exceptionType, s, e, h, c)
        }
        tryRanges = ranges
        handlerIndices = ranges.map { it.handlerIndex }.toSet()
    }

    /** Exclusive catch ends may label the boundary before a payload, rather than executable code. */
    fun boundaryIndex(label: String): Int? = boundaries[label.removePrefix(":")]

    fun codeIndex(label: String): Int? = (labelTargets[label.removePrefix(":")] as? LabelTarget.Code)?.index
    fun payload(label: String): PayloadDirective? = (labelTargets[label.removePrefix(":")] as? LabelTarget.Payload)?.payload
}
