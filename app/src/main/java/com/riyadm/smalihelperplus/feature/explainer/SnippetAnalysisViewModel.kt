package com.riyadm.smalihelperplus.feature.explainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.core.smali.MethodInsight
import com.riyadm.smalihelperplus.core.smali.SmaliAnalysis
import com.riyadm.smalihelperplus.core.smali.SmaliAnalyzer
import com.riyadm.smalihelperplus.core.smali.SmaliSamples
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result state shared by the explainer, register visualiser and control-flow screens. */
data class SnippetAnalysisState(
    val analysis: SmaliAnalysis? = null,
    /** Methods that contain instructions. */
    val methods: List<MethodDefinition> = emptyList(),
    val selectedMethod: Int = 0,
    val insight: MethodInsight? = null,
    val running: Boolean = false,
    val message: String? = null,
    /** Where the analysed code came from ("Playground", a file name, a sample...). */
    val sourceLabel: String? = null,
) {
    val hasResult: Boolean get() = insight != null
}

/**
 * Base view model: holds the input text (as snapshot state for a smooth text field), consumes a [CodeHandoff] for
 * [handoffTarget] and runs the engine off the main thread.
 */
abstract class SnippetAnalysisViewModel(
    private val preferences: UserPreferencesRepository,
    private val codeHandoff: CodeHandoff,
    private val handoffTarget: String,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    var input by mutableStateOf("")
        private set

    private val _state = MutableStateFlow(SnippetAnalysisState())
    val state: StateFlow<SnippetAnalysisState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        consumeHandoff()
    }

    /** Picks up code sent from another screen (called on creation and whenever the screen is shown again). */
    fun consumeHandoff() {
        codeHandoff.consume(handoffTarget)?.let { p ->
            input = p.code
            run(p.title ?: "Sent code")
        }
    }

    fun onInputChange(value: String) {
        input = value
    }

    fun clear() {
        job?.cancel()
        input = ""
        _state.value = SnippetAnalysisState()
    }

    fun loadSample(sample: SmaliSamples.Sample) {
        input = sample.code
        run("Sample: ${sample.title}")
    }

    fun usePlaygroundCode() {
        viewModelScope.launch {
            val code = preferences.preferences.first().lastPlaygroundCode
            if (code.isBlank()) {
                _state.update { it.copy(message = "The Playground is empty. Write or open some code there first.") }
            } else {
                input = code
                run("Playground")
            }
        }
    }

    fun run(sourceLabel: String? = _state.value.sourceLabel) {
        val text = input
        job?.cancel()
        if (text.isBlank()) {
            _state.value = SnippetAnalysisState(message = "Paste some smali code or pick a sample first.")
            return
        }
        if (text.length > SmaliAnalyzer.MAX_INPUT_CHARS) {
            _state.value = SnippetAnalysisState(message = "The code is larger than 2 MB and cannot be analysed here.")
            return
        }
        _state.update { it.copy(running = true, message = null) }
        job = viewModelScope.launch {
            val result = withContext(dispatcher) {
                val analysis = SmaliAnalyzer.analyzeSnippet(text)
                val methods = analysis.file.methods.filter { it.instructions.isNotEmpty() }
                val insight = methods.firstOrNull()?.let { SmaliAnalyzer.insight(analysis.file, it) }
                SnippetAnalysisState(
                    analysis = analysis, methods = methods, selectedMethod = 0, insight = insight,
                    message = if (methods.isEmpty()) "No instructions were found. Add a method body or bare instructions such as `const/4 v0, 0x1`." else null,
                    sourceLabel = sourceLabel,
                )
            }
            _state.value = result
        }
    }

    fun selectMethod(index: Int) {
        val s = _state.value
        val analysis = s.analysis ?: return
        val method = s.methods.getOrNull(index) ?: return
        job?.cancel()
        _state.update { it.copy(selectedMethod = index, running = true) }
        job = viewModelScope.launch {
            val insight = withContext(dispatcher) { SmaliAnalyzer.insight(analysis.file, method) }
            _state.update { it.copy(insight = insight, running = false) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
