package com.riyadm.smalihelperplus.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory hand-off of code between screens (e.g. "Open in Playground" from an opcode example, or "Explain" from
 * the Playground). Avoids putting large strings in navigation arguments / saved state.
 */
@Singleton
class CodeHandoff @Inject constructor() {
    private val _pending = MutableStateFlow<PendingCode?>(null)
    val pending: StateFlow<PendingCode?> = _pending.asStateFlow()

    /** Code for [target] screen ("playground", "explainer", "method_explorer", "control_flow", "ai", "compare_left"...). */
    fun offer(target: String, code: String, title: String? = null) {
        _pending.value = PendingCode(target, code, title)
    }

    /** Returns and clears pending code if it was addressed to [target]. */
    fun consume(target: String): PendingCode? {
        val current = _pending.value
        return if (current != null && current.target == target) {
            _pending.value = null
            current
        } else {
            null
        }
    }

    data class PendingCode(val target: String, val code: String, val title: String?)

    companion object Targets {
        const val PLAYGROUND = "playground"
        const val EXPLAINER = "explainer"
        const val METHOD_EXPLORER = "method_explorer"
        const val CONTROL_FLOW = "control_flow"
        const val REGISTER_VISUALIZER = "register_visualizer"
        const val AI = "ai"
        const val COMPARE = "compare"
    }
}
