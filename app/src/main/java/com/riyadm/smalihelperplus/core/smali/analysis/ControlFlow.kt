package com.riyadm.smalihelperplus.core.smali.analysis

import com.riyadm.smalihelperplus.core.smali.ast.Instruction
import com.riyadm.smalihelperplus.core.smali.ast.LabelOperand
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.opcodes.FlowKind
import com.riyadm.smalihelperplus.core.smali.opcodes.PayloadKind

enum class EdgeKind { FALLTHROUGH, BRANCH_TAKEN, GOTO, SWITCH_CASE, EXCEPTION }

/** Edge between basic blocks. [label] is a short description such as "true", "case 1" or "catch Ljava/io/IOException;". */
data class CfgEdge(val from: Int, val to: Int, val kind: EdgeKind, val label: String)

/** A maximal straight-line run of instructions [startIndex]..[endIndex] (inclusive). */
data class BasicBlock(
    val id: Int,
    val startIndex: Int,
    val endIndex: Int,
    val instructions: List<Instruction>,
    val labels: List<String>,
    val isEntry: Boolean,
    val isHandler: Boolean,
) {
    val name: String get() = "B$id"
}

/** Instruction-level successor (used for data-flow analysis). */
data class InstructionSuccessor(val index: Int, val exceptional: Boolean)

data class ControlFlowGraph(
    val method: MethodDefinition,
    val blocks: List<BasicBlock>,
    val edges: List<CfgEdge>,
    val successors: List<List<InstructionSuccessor>>,
    /** Problems found while building the graph (unresolved labels etc.). */
    val notes: List<String>,
) {
    fun outgoing(block: Int): List<CfgEdge> = edges.filter { it.from == block }
    fun incoming(block: Int): List<CfgEdge> = edges.filter { it.to == block }
    fun blockOf(instructionIndex: Int): BasicBlock? = blocks.firstOrNull { instructionIndex in it.startIndex..it.endIndex }
}

/** Builds basic blocks and edges for a method. */
object ControlFlowAnalyzer {
    fun build(method: MethodDefinition, layout: MethodLayout = MethodLayout(method)): ControlFlowGraph {
        val insns = layout.instructions
        val notes = ArrayList<String>()
        if (insns.isEmpty()) return ControlFlowGraph(method, emptyList(), emptyList(), emptyList(), listOf("The method has no instructions."))

        // Instruction successors.
        val succ = ArrayList<List<InstructionSuccessor>>(insns.size)
        val branchTargets = HashSet<Int>()
        insns.forEachIndexed { i, insn ->
            val list = ArrayList<InstructionSuccessor>()
            val flow = insn.opcode?.flow ?: FlowKind.NORMAL
            fun next() { if (i + 1 < insns.size) list += InstructionSuccessor(i + 1, false) }
            when (flow) {
                FlowKind.NORMAL -> next()
                FlowKind.RETURN, FlowKind.THROW -> Unit
                FlowKind.GOTO -> targetOf(insn, layout, notes)?.let { list += InstructionSuccessor(it, false); branchTargets += it }
                FlowKind.CONDITIONAL -> {
                    next()
                    targetOf(insn, layout, notes)?.let { list += InstructionSuccessor(it, false); branchTargets += it }
                }
                FlowKind.SWITCH -> {
                    next()
                    switchCases(insn, layout, notes).forEach { (_, idx) -> list += InstructionSuccessor(idx, false); branchTargets += idx }
                }
            }
            layout.tryRanges.filter { i >= it.startIndex && i < it.endIndex }.forEach { r ->
                if (r.handlerIndex < insns.size) list += InstructionSuccessor(r.handlerIndex, true)
            }
            succ += list.distinct()
        }

        // Leaders.
        val leaders = sortedSetOf(0)
        layout.labelsAt.keys.filter { it < insns.size }.forEach { leaders += it }
        leaders += branchTargets.filter { it < insns.size }
        layout.tryRanges.forEach { r ->
            if (r.startIndex < insns.size) leaders += r.startIndex
            if (r.endIndex < insns.size) leaders += r.endIndex
            if (r.handlerIndex < insns.size) leaders += r.handlerIndex
        }
        insns.forEachIndexed { i, insn ->
            val flow = insn.opcode?.flow ?: FlowKind.NORMAL
            if (flow != FlowKind.NORMAL && i + 1 < insns.size) leaders += i + 1
        }

        val starts = leaders.toList()
        val blocks = starts.mapIndexed { id, start ->
            val end = (starts.getOrNull(id + 1) ?: insns.size) - 1
            BasicBlock(
                id = id, startIndex = start, endIndex = end, instructions = insns.subList(start, end + 1),
                labels = layout.labelsAt[start].orEmpty(), isEntry = start == 0, isHandler = start in layout.handlerIndices,
            )
        }
        val blockByStart = blocks.associateBy { it.startIndex }
        fun blockFor(index: Int): BasicBlock? = blockByStart[index]

        val edges = LinkedHashSet<CfgEdge>()
        blocks.forEach { b ->
            val last = insns[b.endIndex]
            val flow = last.opcode?.flow ?: FlowKind.NORMAL
            val nextBlock = blockFor(b.endIndex + 1)
            when (flow) {
                FlowKind.NORMAL -> nextBlock?.let { edges += CfgEdge(b.id, it.id, EdgeKind.FALLTHROUGH, "fallthrough") }
                FlowKind.GOTO -> targetOf(last, layout, null)?.let { t -> blockFor(t)?.let { edges += CfgEdge(b.id, it.id, EdgeKind.GOTO, "goto") } }
                FlowKind.CONDITIONAL -> {
                    targetOf(last, layout, null)?.let { t -> blockFor(t)?.let { edges += CfgEdge(b.id, it.id, EdgeKind.BRANCH_TAKEN, "true") } }
                    nextBlock?.let { edges += CfgEdge(b.id, it.id, EdgeKind.FALLTHROUGH, "false") }
                }
                FlowKind.SWITCH -> {
                    switchCases(last, layout, null).forEach { (key, t) ->
                        blockFor(t)?.let { edges += CfgEdge(b.id, it.id, EdgeKind.SWITCH_CASE, "case $key") }
                    }
                    nextBlock?.let { edges += CfgEdge(b.id, it.id, EdgeKind.FALLTHROUGH, "default") }
                }
                FlowKind.RETURN, FlowKind.THROW -> Unit
            }
            layout.tryRanges.filter { r -> r.startIndex <= b.endIndex && r.endIndex > b.startIndex }.forEach { r ->
                blockFor(r.handlerIndex)?.let { edges += CfgEdge(b.id, it.id, EdgeKind.EXCEPTION, if (r.exceptionType == null) "catch all" else "catch ${r.exceptionType}") }
            }
        }
        return ControlFlowGraph(method, blocks, edges.toList(), succ, notes.distinct())
    }

    private fun targetOf(insn: Instruction, layout: MethodLayout, notes: MutableList<String>?): Int? {
        val label = insn.operands.filterIsInstance<LabelOperand>().lastOrNull() ?: return null
        val idx = layout.codeIndex(label.name)
        if (idx == null) notes?.add("Line ${insn.line}: label ${label.text} could not be resolved.")
        return idx?.takeIf { it < layout.instructions.size }
    }

    /** Pairs of (case key, target instruction index). */
    fun switchCases(insn: Instruction, layout: MethodLayout, notes: MutableList<String>?): List<Pair<Long, Int>> {
        val label = insn.operands.filterIsInstance<LabelOperand>().lastOrNull() ?: return emptyList()
        val payload = layout.payload(label.name)
        if (payload == null) {
            notes?.add("Line ${insn.line}: switch payload ${label.text} could not be resolved.")
            return emptyList()
        }
        val out = ArrayList<Pair<Long, Int>>()
        payload.targets.forEachIndexed { i, target ->
            val key = when (payload.kind) {
                PayloadKind.PACKED_SWITCH -> (payload.firstKey ?: 0L) + i
                else -> payload.keys.getOrElse(i) { i.toLong() }
            }
            val idx = layout.codeIndex(target.name)
            if (idx == null || idx >= layout.instructions.size) notes?.add("Line ${payload.line}: case target ${target.text} could not be resolved.")
            else out += key to idx
        }
        return out
    }
}
