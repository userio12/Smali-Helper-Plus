package com.riyadm.smalihelperplus.feature.tools.compare

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.domain.tools.DiffOptions
import com.riyadm.smalihelperplus.domain.tools.DiffResult
import com.riyadm.smalihelperplus.domain.tools.SmaliDiff
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class CompareSide { LEFT, RIGHT }
enum class CompareView { UNIFIED, SIDE_BY_SIDE }

data class CompareUiState(
    val left: String = "",
    val right: String = "",
    val leftName: String = "Left",
    val rightName: String = "Right",
    val options: DiffOptions = DiffOptions(),
    val view: CompareView = CompareView.UNIFIED,
    val result: DiffResult? = null,
    val running: Boolean = false,
    /** One-shot message for the snackbar; cleared by [CodeComparisonViewModel.messageShown]. */
    val message: String? = null,
)

@HiltViewModel
class CodeComparisonViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val handoff: CodeHandoff,
    private val preferences: UserPreferencesRepository,
    @DefaultDispatcher private val default: CoroutineDispatcher,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        handoff.consume(CodeHandoff.COMPARE)?.let { pending ->
            _state.update { it.copy(left = pending.code, leftName = pending.title ?: "Left") }
        }
    }

    fun setText(side: CompareSide, text: String) = _state.update {
        if (side == CompareSide.LEFT) it.copy(left = text.take(MAX_CHARS)) else it.copy(right = text.take(MAX_CHARS))
    }

    fun clear(side: CompareSide) = _state.update {
        if (side == CompareSide.LEFT) it.copy(left = "", leftName = "Left") else it.copy(right = "", rightName = "Right")
    }

    fun swap() = _state.update { it.copy(left = it.right, right = it.left, leftName = it.rightName, rightName = it.leftName, result = null) }

    fun setView(view: CompareView) = _state.update { it.copy(view = view) }

    fun setOptions(options: DiffOptions) {
        _state.update { it.copy(options = options) }
        if (_state.value.result != null) compare()
    }

    fun usePlaygroundCode(side: CompareSide) {
        viewModelScope.launch {
            val code = preferences.preferences.first().lastPlaygroundCode
            if (code.isBlank()) {
                post("The Playground has no saved code yet.")
            } else {
                _state.update {
                    if (side == CompareSide.LEFT) it.copy(left = code.take(MAX_CHARS), leftName = "Playground")
                    else it.copy(right = code.take(MAX_CHARS), rightName = "Playground")
                }
            }
        }
    }

    fun importFile(side: CompareSide, uri: Uri) {
        viewModelScope.launch {
            val loaded = withContext(io) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "file"
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) return@runCatching null
                            out.write(buffer, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: return@runCatching null
                    name to text
                }
            }
            val pair = loaded.getOrNull()
            if (pair == null) {
                post(if (loaded.isFailure) "Could not read that file." else "That file is larger than 2 MB or empty.")
            } else {
                _state.update {
                    if (side == CompareSide.LEFT) it.copy(left = pair.second, leftName = pair.first)
                    else it.copy(right = pair.second, rightName = pair.first)
                }
            }
        }
    }

    fun compare() {
        job?.cancel()
        val snapshot = _state.value
        _state.update { it.copy(running = true) }
        job = viewModelScope.launch {
            val result = withContext(default) { SmaliDiff.diff(snapshot.left, snapshot.right, snapshot.options) }
            _state.update { it.copy(result = result, running = false) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun post(message: String) = _state.update { it.copy(message = message) }

    private companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_CHARS = 2_000_000
    }
}
