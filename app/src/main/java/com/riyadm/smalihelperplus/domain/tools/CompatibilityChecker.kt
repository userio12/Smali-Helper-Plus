package com.riyadm.smalihelperplus.domain.tools

/** The facts the checker needs about one opcode (taken from the opcode database). */
data class OpcodeCompat(val name: String, val minApi: Int, val dexVersion: String)

/** An instruction found in the code that needs a newer API level than the target. */
data class CompatIssue(
    val line: Int,
    val mnemonic: String,
    val lineText: String,
    val requiredApi: Int,
    val dexVersion: String,
)

data class UnknownMnemonic(val line: Int, val mnemonic: String, val lineText: String)

data class CompatReport(
    val targetMinSdk: Int,
    val instructionCount: Int,
    /** Instructions whose minApi is greater than [targetMinSdk]. */
    val issues: List<CompatIssue>,
    val unknown: List<UnknownMnemonic>,
    /** Distinct mnemonic -> number of occurrences (known opcodes only). */
    val usage: Map<String, Int>,
    /** Highest minApi of any recognised instruction (1 if none). */
    val requiredMinApi: Int,
    /** Highest DEX version among recognised instructions ("035" if none). */
    val requiredDexVersion: String,
) {
    val compatible: Boolean get() = issues.isEmpty()
}

/** Finds instructions in smali text that are not available on a given minSdk. */
object CompatibilityChecker {

    private val MNEMONIC = Regex("^[a-z][a-z0-9]*(?:[-/][a-z0-9]+)*$")

    /** Directive blocks whose body is data, not instructions. */
    private val DATA_BLOCKS = mapOf(
        ".annotation" to ".end annotation",
        ".subannotation" to ".end subannotation",
        ".packed-switch" to ".end packed-switch",
        ".sparse-switch" to ".end sparse-switch",
        ".array-data" to ".end array-data",
    )

    data class Instruction(val line: Int, val mnemonic: String, val text: String)

    /** Extracts the instruction lines of smali text (ignores comments, directives, labels and data blocks). */
    fun extractInstructions(code: String): List<Instruction> {
        val out = ArrayList<Instruction>()
        var endMarker: String? = null
        code.split('\n').forEachIndexed { index, raw ->
            val line = SmaliDiff.stripComment(raw.removeSuffix("\r")).trim()
            if (line.isEmpty()) return@forEachIndexed
            val marker = endMarker
            if (marker != null) {
                if (line.startsWith(marker)) endMarker = null
                return@forEachIndexed
            }
            if (line.startsWith(".")) {
                val directive = line.split(Regex("\\s+"), limit = 2)[0]
                DATA_BLOCKS[directive]?.let { endMarker = it }
                return@forEachIndexed
            }
            if (line.startsWith(":")) return@forEachIndexed
            val token = line.split(Regex("\\s+"), limit = 2)[0]
            if (MNEMONIC.matches(token)) out += Instruction(index + 1, token, line)
        }
        return out
    }

    fun check(code: String, targetMinSdk: Int, opcodes: Collection<OpcodeCompat>): CompatReport {
        val byName = opcodes.associateBy { it.name }
        val instructions = extractInstructions(code)
        val issues = ArrayList<CompatIssue>()
        val unknown = ArrayList<UnknownMnemonic>()
        val usage = LinkedHashMap<String, Int>()
        var required = 1
        var dex = "035"
        for (ins in instructions) {
            val op = byName[ins.mnemonic]
            if (op == null) {
                unknown += UnknownMnemonic(ins.line, ins.mnemonic, ins.text)
                continue
            }
            usage[op.name] = (usage[op.name] ?: 0) + 1
            if (op.minApi > required) required = op.minApi
            if (op.dexVersion > dex) dex = op.dexVersion
            if (op.minApi > targetMinSdk) issues += CompatIssue(ins.line, ins.mnemonic, ins.text, op.minApi, op.dexVersion)
        }
        return CompatReport(targetMinSdk, instructions.size, issues, unknown, usage, required, dex)
    }

    /** Opcodes that need more than [baseline] (default API 1), sorted by API level then name. */
    fun newerThan(opcodes: Collection<OpcodeCompat>, baseline: Int = 1): List<OpcodeCompat> =
        opcodes.filter { it.minApi > baseline }.sortedWith(compareBy({ it.minApi }, { it.name }))
}
