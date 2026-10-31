package com.riyadm.smalihelperplus.feature.playground

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.database.dao.RecentFileDao
import com.riyadm.smalihelperplus.core.database.dao.SnippetDao
import com.riyadm.smalihelperplus.core.database.entity.RecentFileEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.core.datastore.UserPreferences
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.core.smali.AnalysisOptions
import com.riyadm.smalihelperplus.core.smali.SmaliAnalyzer
import com.riyadm.smalihelperplus.core.smali.SmaliSamples
import com.riyadm.smalihelperplus.core.smali.diagnostics.Diagnostic
import com.riyadm.smalihelperplus.core.smali.format.SmaliFormatter
import com.riyadm.smalihelperplus.core.smali.structure.ClassOutline
import com.riyadm.smalihelperplus.feature.playground.editor.EditHistory
import com.riyadm.smalihelperplus.feature.playground.editor.EditorLogic
import com.riyadm.smalihelperplus.feature.playground.editor.EditorText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Live analysis of the editor buffer. */
data class EditorAnalysis(
    val diagnostics: List<Diagnostic> = emptyList(),
    val outline: ClassOutline? = null,
    val analyzedLength: Int = 0,
) {
    val errors: Int get() = diagnostics.count { it.severity == com.riyadm.smalihelperplus.core.smali.diagnostics.Severity.ERROR }
    val warnings: Int get() = diagnostics.count { it.severity == com.riyadm.smalihelperplus.core.smali.diagnostics.Severity.WARNING }
}

sealed interface PlaygroundEvent {
    data class Message(val text: String, val offerUndo: Boolean = false) : PlaygroundEvent
}

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val snippetDao: SnippetDao,
    private val recentFileDao: RecentFileDao,
    private val codeHandoff: CodeHandoff,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Editor buffer, held as snapshot state so typing never goes through an asynchronous round trip. */
    var editorValue by mutableStateOf(TextFieldValue(""))
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Display name of the file the buffer came from (or was saved to). */
    var fileName by mutableStateOf<String?>(null)
        private set

    var restored by mutableStateOf(false)
        private set

    private val history = EditHistory(maxEntries = 200)

    private val _analysis = MutableStateFlow(EditorAnalysis())
    val analysis: StateFlow<EditorAnalysis> = _analysis.asStateFlow()

    val preferences: StateFlow<UserPreferences?> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentFiles: StateFlow<List<RecentFileEntity>> = recentFileDao.observeRecent(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<String>> = snippetDao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val events = Channel<PlaygroundEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var bufferTooLargeToPersistNotified = false

    init {
        viewModelScope.launch {
            // Restore the last buffer first, then let pending hand-offs (from other screens) replace it.
            val last = preferencesRepository.preferences.first().lastPlaygroundCode
            if (editorValue.text.isEmpty()) {
                editorValue = TextFieldValue(last.ifEmpty { SmaliSamples.STARTER })
            }
            restored = true
            codeHandoff.pending.collect { pending ->
                if (pending?.target == CodeHandoff.PLAYGROUND) {
                    codeHandoff.consume(CodeHandoff.PLAYGROUND)?.let { p ->
                        replaceAll(p.code, force = true)
                        fileName = p.title
                        send("Loaded ${p.title ?: "code"} into the Playground", offerUndo = true)
                    }
                }
            }
        }
        viewModelScope.launch {
            snapshotFlow { editorValue.text }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest { text ->
                    val result = withContext(defaultDispatcher) {
                        val a = SmaliAnalyzer.analyze(text, AnalysisOptions(minApi = MIN_API))
                        EditorAnalysis(a.diagnostics, a.outline, text.length)
                    }
                    _analysis.value = result
                }
        }
        viewModelScope.launch {
            snapshotFlow { editorValue.text }
                .drop(1)
                .distinctUntilChanged()
                .debounce(1_000)
                .collectLatest { text ->
                    if (!restored) return@collectLatest
                    if (text.length <= MAX_PERSISTED_CHARS) {
                        preferencesRepository.setLastPlaygroundCode(text)
                    } else if (!bufferTooLargeToPersistNotified) {
                        bufferTooLargeToPersistNotified = true
                        send("This buffer is too large to be restored automatically; save it as a file to keep it")
                    }
                }
        }
    }

    private fun send(text: String, offerUndo: Boolean = false) {
        events.trySend(PlaygroundEvent.Message(text, offerUndo))
    }

    private fun TextFieldValue.toEditorText() = EditorText(text, selection.start, selection.end)
    private fun EditorText.toValue() = TextFieldValue(text, TextRange(selectionStart, selectionEnd))

    private fun updateHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Editing
    // -----------------------------------------------------------------------------------------------------------------

    fun onValueChange(newValue: TextFieldValue) {
        val old = editorValue
        if (newValue.text == old.text) {
            editorValue = newValue // selection/composition only
            return
        }
        val indented = EditorLogic.autoIndent(old.toEditorText(), newValue.toEditorText())
        val next = indented?.toValue() ?: newValue
        history.record(old.toEditorText(), next.toEditorText(), System.currentTimeMillis())
        editorValue = next
        updateHistoryFlags()
    }

    /** Replaces the whole buffer as one undoable step. */
    fun replaceAll(text: String, cursor: Int = 0, force: Boolean = true) {
        val old = editorValue
        val next = TextFieldValue(text, TextRange(cursor.coerceIn(0, text.length)))
        history.record(old.toEditorText(), next.toEditorText(), System.currentTimeMillis(), force = force)
        editorValue = next
        updateHistoryFlags()
    }

    fun select(start: Int, end: Int = start) {
        val len = editorValue.text.length
        editorValue = editorValue.copy(selection = TextRange(start.coerceIn(0, len), end.coerceIn(0, len)))
    }

    fun undo() {
        val prev = history.undo(editorValue.toEditorText()) ?: return
        editorValue = prev.toValue()
        updateHistoryFlags()
    }

    fun redo() {
        val next = history.redo(editorValue.toEditorText()) ?: return
        editorValue = next.toValue()
        updateHistoryFlags()
    }

    fun insertAtCursor(text: String) {
        val v = editorValue
        val s = minOf(v.selection.start, v.selection.end)
        val e = maxOf(v.selection.start, v.selection.end)
        val newText = v.text.substring(0, s) + text + v.text.substring(e)
        if (newText.length > SmaliAnalyzer.MAX_INPUT_CHARS) {
            send("Paste refused: the buffer would exceed 2 MB")
            return
        }
        replaceAll(newText, s + text.length)
    }

    fun clear() {
        if (editorValue.text.isEmpty()) return
        replaceAll("", 0)
        fileName = null
        send("Editor cleared", offerUndo = true)
    }

    fun applyCompletion(completion: EditorLogic.Completion, suggestion: String) {
        val result = EditorLogic.applyCompletion(editorValue.text, completion, suggestion)
        val old = editorValue
        history.record(old.toEditorText(), result, System.currentTimeMillis(), force = true)
        editorValue = result.toValue()
        updateHistoryFlags()
    }

    fun format() {
        val v = editorValue
        val result = SmaliFormatter.format(v.text)
        when {
            result.refused -> send(result.diagnostics.firstOrNull()?.message ?: "The code could not be formatted safely")
            !result.changed -> send("Already formatted")
            else -> {
                // Keep the cursor on the same line.
                val starts = EditorLogic.lineStarts(v.text)
                val line = EditorLogic.lineOf(starts, v.selection.end.coerceIn(0, v.text.length))
                val newStarts = EditorLogic.lineStarts(result.text)
                replaceAll(result.text, newStarts.getOrElse(line) { result.text.length })
                send("Formatted", offerUndo = true)
            }
        }
    }

    fun replaceAllMatches(query: String, replacement: String, matchCase: Boolean) {
        val (text, count) = EditorLogic.replaceAll(editorValue.text, query, replacement, matchCase)
        if (count == 0) {
            send("No matches")
        } else {
            replaceAll(text, editorValue.selection.end.coerceAtMost(text.length))
            send("Replaced $count occurrence${if (count == 1) "" else "s"}", offerUndo = true)
        }
    }

    fun replaceRange(range: IntRange, replacement: String) {
        val v = editorValue
        if (range.first < 0 || range.last >= v.text.length) return
        val newText = v.text.substring(0, range.first) + replacement + v.text.substring(range.last + 1)
        replaceAll(newText, range.first + replacement.length)
    }

    fun loadSample(sample: SmaliSamples.Sample) {
        replaceAll(sample.code, 0)
        fileName = null
        send("Loaded sample: ${sample.title}", offerUndo = true)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Preferences
    // -----------------------------------------------------------------------------------------------------------------

    fun setWordWrap(value: Boolean) = viewModelScope.launch { preferencesRepository.setEditorWordWrap(value) }
    fun setShowLineNumbers(value: Boolean) = viewModelScope.launch { preferencesRepository.setEditorShowLineNumbers(value) }
    fun changeFontSize(delta: Float) = viewModelScope.launch {
        val current = preferences.value?.editorFontSizeSp ?: 14f
        preferencesRepository.setEditorFontSize(current + delta)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Hand-off and snippets
    // -----------------------------------------------------------------------------------------------------------------

    fun sendTo(target: String) {
        codeHandoff.offer(target, editorValue.text, fileName ?: "Playground")
    }

    fun saveSnippet(title: String, collection: String, description: String) {
        val code = editorValue.text
        if (title.isBlank()) { send("Enter a title"); return }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            withContext(ioDispatcher) {
                snippetDao.insert(
                    SnippetEntity(
                        title = title.trim(), description = description.trim(),
                        collection = collection.trim().ifEmpty { "My snippets" }, code = code,
                        createdAtMillis = now, updatedAtMillis = now,
                    ),
                )
            }
            send("Saved \"${title.trim()}\" to ${collection.trim().ifEmpty { "My snippets" }}")
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Files (Storage Access Framework)
    // -----------------------------------------------------------------------------------------------------------------

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun size(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    /** Opens a document picked with ACTION_OPEN_DOCUMENT (or from the recent list). */
    fun openDocument(uri: Uri, fromRecents: Boolean = false) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { readDocument(uri, takePermission = !fromRecents) }
            result.fold(
                onSuccess = { (name, text) ->
                    replaceAll(text, 0)
                    fileName = name
                    recentFileDao.upsert(RecentFileEntity(uri.toString(), name, System.currentTimeMillis()))
                    send("Opened $name", offerUndo = true)
                },
                onFailure = { e ->
                    if (fromRecents && (e is SecurityException || e is IOException || e is IllegalArgumentException)) {
                        recentFileDao.delete(uri.toString())
                        send("Access to this file is no longer available; it was removed from recent files")
                    } else {
                        send(e.message ?: "Could not open the file")
                    }
                },
            )
        }
    }

    private fun readDocument(uri: Uri, takePermission: Boolean): Result<Pair<String, String>> = runCatching {
        val name = displayName(uri) ?: uri.lastPathSegment ?: "file.smali"
        if (!name.endsWith(".smali", ignoreCase = true)) {
            throw IllegalStateException("\"$name\" is not a .smali file")
        }
        val declared = size(uri)
        if (declared != null && declared > MAX_FILE_BYTES) throw IllegalStateException("\"$name\" is larger than 2 MB")
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw IllegalStateException("\"$name\" is larger than 2 MB")
                buffer.write(chunk, 0, n)
            }
            buffer.toByteArray()
        } ?: throw IOException("Could not read \"$name\"")
        if (takePermission) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // The provider does not offer persistable grants; the file can still be opened now.
            }
        }
        name to bytes.toString(Charsets.UTF_8)
    }

    /** Writes the buffer to a document created with ACTION_CREATE_DOCUMENT. */
    fun saveDocument(uri: Uri) {
        val text = editorValue.text
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Could not write the file")
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) {
                        // Not persistable: saving still succeeded.
                    }
                    displayName(uri) ?: "Snippet.smali"
                }
            }
            result.fold(
                onSuccess = { name ->
                    fileName = name
                    if (name.endsWith(".smali", ignoreCase = true)) {
                        recentFileDao.upsert(RecentFileEntity(uri.toString(), name, System.currentTimeMillis()))
                    }
                    send("Saved $name")
                },
                onFailure = { send(it.message ?: "Could not save the file") },
            )
        }
    }

    fun clearRecentFiles() = viewModelScope.launch { recentFileDao.clear() }

    fun removeRecentFile(uri: String) = viewModelScope.launch { recentFileDao.delete(uri) }

    companion object {
        const val MIN_API = 26
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        const val MAX_PERSISTED_CHARS = 1_000_000
    }
}
