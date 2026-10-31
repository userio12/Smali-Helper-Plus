package com.riyadm.smalihelperplus.feature.analysis

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.core.smali.SmaliAnalysis
import com.riyadm.smalihelperplus.core.smali.SmaliAnalyzer
import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.structure.StructureAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One analysed source (file, Playground buffer or hand-off). */
data class ExplorerSource(val id: Long, val name: String, val text: String, val analysis: SmaliAnalysis)

data class MethodExplorerState(
    val sources: List<ExplorerSource> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val query: String = "",
)

@HiltViewModel
class MethodExplorerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val codeHandoff: CodeHandoff,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(MethodExplorerState())
    val state: StateFlow<MethodExplorerState> = _state.asStateFlow()
    private var nextId = 1L

    init {
        consumeHandoff()
    }

    fun consumeHandoff() {
        codeHandoff.consume(CodeHandoff.METHOD_EXPLORER)?.let { addSource(it.title ?: "Sent code", it.code) }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun removeSource(id: Long) = _state.update { s -> s.copy(sources = s.sources.filterNot { it.id == id }) }

    fun usePlaygroundCode() {
        viewModelScope.launch {
            val code = preferences.preferences.first().lastPlaygroundCode
            if (code.isBlank()) _state.update { it.copy(message = "The Playground is empty.") } else addSource("Playground", code)
        }
    }

    private fun addSource(name: String, text: String) {
        if (text.length > SmaliAnalyzer.MAX_INPUT_CHARS) {
            _state.update { it.copy(message = "\"$name\" is larger than 2 MB and was skipped.") }
            return
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val analysis = withContext(defaultDispatcher) { SmaliAnalyzer.analyzeSnippet(text) }
            val source = ExplorerSource(nextId++, name, text, analysis)
            _state.update { s ->
                // Replace an older source with the same name (e.g. re-importing the Playground buffer).
                s.copy(sources = s.sources.filterNot { it.name == name } + source, loading = false)
            }
        }
    }

    fun openDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val problems = ArrayList<String>()
            val loaded = withContext(ioDispatcher) {
                uris.take(MAX_FILES).mapNotNull { uri ->
                    runCatching { read(uri) }.onFailure { problems += it.message ?: "A file could not be read" }.getOrNull()
                }
            }
            if (uris.size > MAX_FILES) problems += "Only the first $MAX_FILES files were opened."
            val sources = withContext(defaultDispatcher) {
                loaded.map { (name, text) -> ExplorerSource(nextId++, name, text, SmaliAnalyzer.analyzeSnippet(text)) }
            }
            _state.update { s ->
                val names = sources.map { it.name }.toSet()
                s.copy(
                    sources = s.sources.filterNot { it.name in names } + sources,
                    loading = false,
                    message = problems.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                )
            }
        }
    }

    private fun read(uri: Uri): Pair<String, String> {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment ?: "file.smali"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (!name.endsWith(".smali", ignoreCase = true)) throw IllegalStateException("\"$name\" is not a .smali file")
        if ((size ?: 0L) > MAX_FILE_BYTES) throw IllegalStateException("\"$name\" is larger than 2 MB")
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw IllegalStateException("\"$name\" is larger than 2 MB")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: throw IllegalStateException("\"$name\" could not be read")
        return name to bytes.toString(Charsets.UTF_8)
    }

    /** Offers a method (wrapped with its class header) to another screen. */
    fun offerMethod(source: ExplorerSource, method: MethodDefinition, target: String) {
        val code = if (method.isImplicit || source.analysis.file.header == null) {
            StructureAnalyzer.methodSource(source.text, method)
        } else {
            StructureAnalyzer.standaloneMethodSource(source.text, source.analysis.file, method)
        }
        codeHandoff.offer(target, code, "${source.name} · ${method.name}")
    }

    companion object {
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        const val MAX_FILES = 20
    }
}
