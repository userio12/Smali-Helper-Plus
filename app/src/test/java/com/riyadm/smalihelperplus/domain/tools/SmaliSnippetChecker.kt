package com.riyadm.smalihelperplus.domain.tools

/**
 * Test-only structural validator for smali snippets: balanced directives, defined labels, known mnemonics,
 * registers inside the declared frame and operand-width limits. It is intentionally strict so that bundled
 * snippets are guaranteed to be internally consistent.
 */
object SmaliSnippetChecker {

    val KNOWN_MNEMONICS: Set<String> = buildSet {
        addAll(listOf("nop", "return-void", "monitor-enter", "monitor-exit", "check-cast", "instance-of", "array-length", "new-instance", "new-array"))
        addAll(listOf("filled-new-array", "filled-new-array/range", "fill-array-data", "throw", "goto", "goto/16", "goto/32", "packed-switch", "sparse-switch"))
        addAll(listOf("cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double", "cmp-long"))
        for (k in listOf("", "-wide", "-object")) {
            add("move$k"); add("move$k/from16"); add("move$k/16"); add("return$k")
        }
        addAll(listOf("move-result", "move-result-wide", "move-result-object", "move-exception"))
        addAll(listOf("const/4", "const/16", "const", "const/high16", "const-wide/16", "const-wide/32", "const-wide", "const-wide/high16"))
        addAll(listOf("const-string", "const-string/jumbo", "const-class", "const-method-handle", "const-method-type"))
        for (c in listOf("eq", "ne", "lt", "ge", "gt", "le")) { add("if-$c"); add("if-${c}z") }
        for (t in listOf("", "-wide", "-object", "-boolean", "-byte", "-char", "-short")) {
            for (p in listOf("aget", "aput", "iget", "iput", "sget", "sput")) add("$p$t")
        }
        for (k in listOf("virtual", "super", "direct", "static", "interface")) { add("invoke-$k"); add("invoke-$k/range") }
        addAll(listOf("invoke-polymorphic", "invoke-polymorphic/range", "invoke-custom", "invoke-custom/range"))
        addAll(listOf("neg-int", "not-int", "neg-long", "not-long", "neg-float", "neg-double"))
        for (f in listOf("int", "long", "float", "double")) for (t in listOf("int", "long", "float", "double")) if (f != t) add("$f-to-$t")
        addAll(listOf("int-to-byte", "int-to-char", "int-to-short"))
        for (t in listOf("int", "long")) for (o in listOf("add", "sub", "mul", "div", "rem", "and", "or", "xor", "shl", "shr", "ushr")) {
            add("$o-$t"); add("$o-$t/2addr")
        }
        for (t in listOf("float", "double")) for (o in listOf("add", "sub", "mul", "div", "rem")) { add("$o-$t"); add("$o-$t/2addr") }
        for (o in listOf("add", "rsub", "mul", "div", "rem", "and", "or", "xor")) add("$o-int/lit16")
        for (o in listOf("add", "rsub", "mul", "div", "rem", "and", "or", "xor", "shl", "shr", "ushr")) add("$o-int/lit8")
    }

    private val FOUR_BIT = Regex(
        "^(move|move-wide|move-object|array-length|instance-of|new-array|if-(eq|ne|lt|ge|gt|le)|" +
            "(neg|not)-(int|long|float|double)|(int|long|float|double)-to-(int|long|float|double|byte|char|short)|" +
            "i(get|put)(-[a-z]+)?|.+/2addr|.+-int/lit16)$",
    )

    private val WIDE_FIRST = Regex(
        "^(const-wide.*|move-wide.*|return-wide|move-result-wide|(s|i|a)(get|put)-wide|" +
            "(add|sub|mul|div|rem|and|or|xor|shl|shr|ushr)-(long|double)(/2addr)?|(neg|not)-(long|double)|" +
            "(int|float)-to-(long|double)|(long|double)-to-(int|long|float|double))$",
    )

    private val DATA_BLOCKS = mapOf(
        ".annotation" to ".end annotation", ".subannotation" to ".end subannotation",
        ".packed-switch" to ".end packed-switch", ".sparse-switch" to ".end sparse-switch", ".array-data" to ".end array-data",
    )

    private class Ins(val line: Int, val mnemonic: String, val operands: String)

    fun check(code: String): List<String> {
        val errors = ArrayList<String>()
        val lines = code.split('\n')
        var classCount = 0
        var superCount = 0
        var inMethod = false
        var methodName = ""
        var hasBody = true
        var declared = false
        var total = 0
        var paramWords = 0
        val defined = HashSet<String>()
        val referenced = ArrayList<Pair<String, Int>>()
        val instructions = ArrayList<Ins>()
        var endMarker: String? = null
        var abstractOrNative = false

        fun finishMethod(endLine: Int) {
            if (!abstractOrNative && !declared) errors += "$methodName: missing .locals/.registers"
            for ((label, line) in referenced) if (label !in defined) errors += "$methodName line $line: undefined label :$label"
            for (ins in instructions) checkInstruction(ins, total, paramWords, methodName, errors)
            defined.clear(); referenced.clear(); instructions.clear()
        }

        for ((i, raw) in lines.withIndex()) {
            val lineNo = i + 1
            val line = SmaliDiff.stripComment(raw).trim()
            if (line.isEmpty()) continue
            if (endMarker != null) {
                // Inside a data block: collect label references for switch tables.
                if (line.startsWith(endMarker!!)) { endMarker = null; continue }
                Regex(":([A-Za-z0-9_]+)").findAll(line).forEach { referenced += it.groupValues[1] to lineNo }
                continue
            }
            when {
                line.startsWith(".class") -> classCount++
                line.startsWith(".super") -> superCount++
                line.startsWith(".method") -> {
                    if (inMethod) errors += "line $lineNo: nested .method"
                    inMethod = true
                    val tokens = line.removePrefix(".method").trim().split(Regex("\\s+"))
                    val sig = tokens.last()
                    methodName = sig.substringBefore('(')
                    val flags = tokens.dropLast(1)
                    abstractOrNative = "abstract" in flags || "native" in flags
                    declared = false
                    try {
                        val proto = DescriptorConverter.parseProto(sig.substring(sig.indexOf('(')))
                        paramWords = proto.paramWords + if ("static" in flags) 0 else 1
                    } catch (e: Exception) {
                        errors += "$sig: bad signature (${e.message})"
                    }
                }
                line == ".end method" -> {
                    if (!inMethod) errors += "line $lineNo: stray .end method"
                    finishMethod(lineNo)
                    inMethod = false
                }
                line.startsWith(".locals") -> {
                    declared = true
                    total = line.removePrefix(".locals").trim().toInt() + paramWords
                }
                line.startsWith(".registers") -> {
                    declared = true
                    total = line.removePrefix(".registers").trim().toInt()
                }
                line.startsWith(".catch") -> {
                    Regex(":([A-Za-z0-9_]+)").findAll(line).forEach { referenced += it.groupValues[1] to lineNo }
                }
                line.startsWith(".") -> {
                    val d = line.split(Regex("\\s+"))[0]
                    DATA_BLOCKS[d]?.let { endMarker = it }
                }
                line.startsWith(":") -> {
                    if (!inMethod) errors += "line $lineNo: label outside method"
                    if (!defined.add(line.substring(1))) errors += "$methodName line $lineNo: duplicate label $line"
                }
                else -> {
                    if (!inMethod) { errors += "line $lineNo: instruction outside method: $line"; continue }
                    val parts = line.split(Regex("\\s+"), limit = 2)
                    val mnemonic = parts[0]
                    if (mnemonic !in KNOWN_MNEMONICS) errors += "$methodName line $lineNo: unknown mnemonic $mnemonic"
                    val ops = parts.getOrElse(1) { "" }
                    Regex("(?<![A-Za-z0-9_/$;>-]):([A-Za-z0-9_]+)").findAll(ops).forEach { referenced += it.groupValues[1] to lineNo }
                    instructions += Ins(lineNo, mnemonic, ops)
                }
            }
        }
        if (inMethod) errors += "missing .end method"
        if (endMarker != null) errors += "unterminated data block"
        if (classCount > 1) errors += "multiple .class directives"
        if (classCount == 1 && superCount != 1) errors += "class needs exactly one .super"
        return errors
    }

    private fun registersIn(operands: String): List<Pair<Char, Int>> {
        var s = operands.replace(Regex("\"(\\\\.|[^\"\\\\])*\""), "")
        s = s.replace(Regex("\\[*L[^;\\s]*;"), "").replace(Regex("->[^\\s(:]+"), "")
        return Regex("\\b([vp])(\\d+)\\b").findAll(s).map { it.groupValues[1][0] to it.groupValues[2].toInt() }.toList()
    }

    private fun checkInstruction(ins: Ins, total: Int, paramWords: Int, method: String, errors: MutableList<String>) {
        val regs = registersIn(ins.operands)
        val locals = total - paramWords
        fun absolute(r: Pair<Char, Int>) = if (r.first == 'v') r.second else locals + r.second
        for (r in regs) {
            if (r.first == 'p' && r.second >= paramWords) errors += "$method line ${ins.line}: p${r.second} beyond $paramWords parameter words"
            if (absolute(r) >= total) errors += "$method line ${ins.line}: register ${r.first}${r.second} outside frame of $total"
        }
        if (FOUR_BIT.matches(ins.mnemonic) && !ins.mnemonic.startsWith("if-") || Regex("^if-(eq|ne|lt|ge|gt|le)$").matches(ins.mnemonic)) {
            if (!ins.mnemonic.endsWith("from16") && !ins.mnemonic.endsWith("/16") || ins.mnemonic.endsWith("lit16")) {
                for (r in regs) if (absolute(r) > 15) errors += "$method line ${ins.line}: ${ins.mnemonic} cannot address ${r.first}${r.second} (v${absolute(r)} > v15)"
            }
        }
        if (ins.mnemonic.startsWith("invoke-") && !ins.mnemonic.endsWith("/range")) {
            val list = Regex("\\{([^}]*)\\}").find(ins.operands)?.groupValues?.get(1).orEmpty()
            val count = if (list.isBlank()) 0 else list.split(',').size
            if (count > 5) errors += "$method line ${ins.line}: ${ins.mnemonic} with $count registers (max 5)"
            for (r in regs) if (absolute(r) > 15) errors += "$method line ${ins.line}: invoke register ${r.first}${r.second} above v15"
        }
        if (WIDE_FIRST.matches(ins.mnemonic) && regs.isNotEmpty()) {
            val first = absolute(regs[0])
            if (first + 1 >= total) errors += "$method line ${ins.line}: wide register pair starting at v$first exceeds frame of $total"
        }
    }
}
