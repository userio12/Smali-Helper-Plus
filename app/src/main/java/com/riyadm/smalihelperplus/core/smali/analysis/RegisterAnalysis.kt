package com.riyadm.smalihelperplus.core.smali.analysis

import com.riyadm.smalihelperplus.core.smali.ast.Instruction
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.RegisterListOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterOperand
import com.riyadm.smalihelperplus.core.smali.ast.RegisterRangeOperand
import com.riyadm.smalihelperplus.core.smali.opcodes.Access
import com.riyadm.smalihelperplus.core.smali.opcodes.OperandSpec

/** Registers read and written by one instruction. Wide operands contribute both halves of the pair. */
data class InstructionUsage(
    val index: Int,
    val instruction: Instruction,
    val reads: Set<RegKey>,
    val writes: Set<RegKey>,
    /** Registers accessed as part of a 64-bit pair. */
    val wide: Set<RegKey>,
)

/** Summary of one register across a method. Indices refer to instruction indices. */
data class RegisterSummary(
    val key: RegKey,
    val display: String,
    val firstDefinition: Int?,
    val lastUse: Int?,
    val reads: Int,
    val writes: Int,
    /** Maximal runs of instructions (in source order) at which the register holds a value that is used later. */
    val liveRanges: List<IntRange>,
    val usedAsWide: Boolean,
    /** Incoming value (typically a parameter) that is read before being overwritten. */
    val liveAtEntry: Boolean,
    val parameter: ParameterSlot?,
)

data class RegisterAnalysis(
    val frame: RegisterFrame,
    val usages: List<InstructionUsage>,
    /** All registers in display order. */
    val registers: List<RegKey>,
    /** Registers holding a live value at each instruction (live-in, plus definitions that are used later). */
    val live: List<Set<RegKey>>,
    val summaries: Map<RegKey, RegisterSummary>,
)

object RegisterAnalyzer {
    /** Registers read and written by [insn]. */
    fun usage(insn: Instruction, frame: RegisterFrame): InstructionUsage {
        val reads = LinkedHashSet<RegKey>()
        val writes = LinkedHashSet<RegKey>()
        val wide = LinkedHashSet<RegKey>()
        val op = insn.opcode
        if (op != null) {
            op.operands.zip(insn.operands).forEach { (spec, operand) ->
                when (spec) {
                    is OperandSpec.Register -> if (operand is RegisterOperand) {
                        val k = frame.key(operand)
                        val keys = if (spec.wide) listOf(k, k.next()) else listOf(k)
                        if (spec.wide) wide += keys
                        if (spec.access == Access.READ || spec.access == Access.READ_WRITE) reads += keys
                        if (spec.access == Access.WRITE || spec.access == Access.READ_WRITE) writes += keys
                    }
                    is OperandSpec.RegisterList, OperandSpec.RegisterRange -> when (operand) {
                        is RegisterListOperand -> operand.registers.forEach { reads += frame.key(it) }
                        is RegisterRangeOperand -> {
                            val a = frame.key(operand.first)
                            val b = frame.key(operand.last)
                            if (a.prefix == b.prefix && b.number >= a.number && b.number - a.number < 256) {
                                for (n in a.number..b.number) reads += RegKey(a.prefix, n)
                            }
                        }
                        else -> Unit
                    }
                    else -> Unit
                }
            }
        }
        return InstructionUsage(insn.index, insn, reads, writes, wide)
    }

    fun analyze(method: MethodDefinition, declaringClass: String?, cfg: ControlFlowGraph? = null): RegisterAnalysis {
        val frame = RegisterFrame(method, declaringClass)
        val layout = MethodLayout(method)
        val graph = cfg ?: ControlFlowAnalyzer.build(method, layout)
        val insns = layout.instructions
        val usages = insns.map { usage(it, frame) }
        val n = insns.size

        // Backward liveness to a fixpoint. Exceptional successors do not see this instruction's definitions.
        val liveIn = Array(n) { HashSet<RegKey>() }
        val liveOut = Array(n) { HashSet<RegKey>() }
        var changed = true
        var guard = 0
        while (changed && guard++ < 1000) {
            changed = false
            for (i in n - 1 downTo 0) {
                val normalOut = HashSet<RegKey>()
                val excOut = HashSet<RegKey>()
                graph.successors.getOrNull(i)?.forEach { s ->
                    if (s.index in 0 until n) (if (s.exceptional) excOut else normalOut).addAll(liveIn[s.index])
                }
                val newIn = HashSet<RegKey>(usages[i].reads)
                normalOut.forEach { if (it !in usages[i].writes) newIn += it }
                newIn += excOut
                val newOut = HashSet<RegKey>(normalOut).apply { addAll(excOut) }
                if (newIn != liveIn[i] || newOut != liveOut[i]) {
                    liveIn[i] = newIn
                    liveOut[i] = newOut
                    changed = true
                }
            }
        }
        val live = (0 until n).map { i ->
            val s = HashSet(liveIn[i])
            usages[i].writes.filter { it in liveOut[i] }.forEach { s += it }
            s as Set<RegKey>
        }

        val all = sortedSetOf<RegKey>()
        usages.forEach { all.addAll(it.reads); all.addAll(it.writes) }
        val slots = frame.parameterSlots
        val locals = frame.locals
        if (frame.isKnown && locals != null) slots.indices.forEach { all += RegKey('v', locals + it) }

        val summaries = LinkedHashMap<RegKey, RegisterSummary>()
        for (key in all) {
            val firstDef = usages.firstOrNull { key in it.writes }?.index
            val lastUse = usages.lastOrNull { key in it.reads }?.index
            val ranges = ArrayList<IntRange>()
            var start = -1
            for (i in 0 until n) {
                val isLive = key in live[i]
                if (isLive && start < 0) start = i
                if (!isLive && start >= 0) { ranges += start until i; start = -1 }
            }
            if (start >= 0) ranges += start until n
            val slot = when {
                key.prefix == 'v' && frame.isKnown && locals != null && key.number >= locals -> slots.getOrNull(key.number - locals)
                key.prefix == 'p' -> slots.getOrNull(key.number)
                else -> null
            }
            summaries[key] = RegisterSummary(
                key = key,
                display = frame.display(key),
                firstDefinition = firstDef,
                lastUse = lastUse,
                reads = usages.count { key in it.reads },
                writes = usages.count { key in it.writes },
                liveRanges = ranges,
                usedAsWide = usages.any { key in it.wide },
                liveAtEntry = n > 0 && key in liveIn[0],
                parameter = slot,
            )
        }
        return RegisterAnalysis(frame, usages, all.toList(), live, summaries)
    }
}
