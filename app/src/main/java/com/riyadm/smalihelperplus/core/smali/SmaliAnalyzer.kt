package com.riyadm.smalihelperplus.core.smali

import com.riyadm.smalihelperplus.core.smali.analysis.ControlFlowAnalyzer
import com.riyadm.smalihelperplus.core.smali.analysis.ControlFlowGraph
import com.riyadm.smalihelperplus.core.smali.analysis.MethodLayout
import com.riyadm.smalihelperplus.core.smali.analysis.RegisterAnalysis
import com.riyadm.smalihelperplus.core.smali.analysis.RegisterAnalyzer
import com.riyadm.smalihelperplus.core.smali.analysis.SmaliValidator
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.SmaliFile
import com.riyadm.smalihelperplus.core.smali.diagnostics.Diagnostic
import com.riyadm.smalihelperplus.core.smali.diagnostics.Severity
import com.riyadm.smalihelperplus.core.smali.explain.InstructionExplainer
import com.riyadm.smalihelperplus.core.smali.explain.InstructionExplanation
import com.riyadm.smalihelperplus.core.smali.explain.PseudocodeGenerator
import com.riyadm.smalihelperplus.core.smali.parser.ParseMode
import com.riyadm.smalihelperplus.core.smali.parser.SmaliParser
import com.riyadm.smalihelperplus.core.smali.structure.ClassOutline
import com.riyadm.smalihelperplus.core.smali.structure.StructureAnalyzer

data class AnalysisOptions(
    /** Minimum Android API level used for API-level warnings. */
    val minApi: Int = 26,
    val mode: ParseMode = ParseMode.FILE,
)

/** Parse tree plus all diagnostics, sorted by position. */
data class SmaliAnalysis(val file: SmaliFile, val diagnostics: List<Diagnostic>) {
    val errorCount: Int get() = diagnostics.count { it.severity == Severity.ERROR }
    val warningCount: Int get() = diagnostics.count { it.severity == Severity.WARNING }
    val outline: ClassOutline by lazy(LazyThreadSafetyMode.NONE) { StructureAnalyzer.outline(file) }
}

/** Everything the explainer screens need for one method. */
data class MethodInsight(
    val method: MethodDefinition,
    val steps: List<InstructionExplanation>,
    val pseudocode: String,
    val registers: RegisterAnalysis,
    val controlFlow: ControlFlowGraph,
)

/**
 * Entry point of the smali engine: static parsing, validation and educational analysis. Nothing is assembled or
 * executed.
 */
object SmaliAnalyzer {
    /** Upper bound for inputs accepted by the UI (characters). */
    const val MAX_INPUT_CHARS = 2 * 1024 * 1024

    fun analyze(text: String, options: AnalysisOptions = AnalysisOptions()): SmaliAnalysis {
        val parsed = SmaliParser.parse(text, options.mode)
        val semantic = SmaliValidator(options.minApi).validate(parsed.file)
        val all = (parsed.diagnostics + semantic).sortedWith(compareBy({ it.line }, { it.startColumn }, { it.severity }))
        return SmaliAnalysis(parsed.file, all)
    }

    /** Lenient analysis used by the explainer/visualiser screens: accepts bare instruction snippets. */
    fun analyzeSnippet(text: String, minApi: Int = 26): SmaliAnalysis =
        analyze(text, AnalysisOptions(minApi = minApi, mode = ParseMode.LENIENT))

    fun insight(file: SmaliFile, method: MethodDefinition): MethodInsight {
        val layout = MethodLayout(method)
        val cfg = ControlFlowAnalyzer.build(method, layout)
        return MethodInsight(
            method = method,
            steps = method.instructions.map { InstructionExplainer.explain(it, method, layout) },
            pseudocode = PseudocodeGenerator.generate(file, method),
            registers = RegisterAnalyzer.analyze(method, file.className, cfg),
            controlFlow = cfg,
        )
    }
}
