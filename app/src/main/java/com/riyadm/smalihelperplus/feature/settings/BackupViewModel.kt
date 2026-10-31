package com.riyadm.smalihelperplus.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.data.backup.BackupCounts
import com.riyadm.smalihelperplus.data.backup.BackupException
import com.riyadm.smalihelperplus.data.backup.BackupRepository
import com.riyadm.smalihelperplus.data.backup.ImportMode
import com.riyadm.smalihelperplus.data.backup.ImportSummary
import com.riyadm.smalihelperplus.data.backup.ParsedBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A validated file waiting for the user's Merge/Replace decision. */
data class PendingImport(val parsed: ParsedBackup, val bookmarksOnlyRequested: Boolean, val mode: ImportMode = ImportMode.MERGE) {
    /** Bookmarks-only imports (requested, or the file only holds bookmarks) can only be merged. */
    val bookmarksOnly: Boolean get() = bookmarksOnlyRequested || parsed.isBookmarksOnly
    val counts: BackupCounts
        get() = if (bookmarksOnly) BackupCounts(bookmarks = parsed.counts.bookmarks) else parsed.counts
}

data class BackupUiState(
    val counts: BackupCounts = BackupCounts(),
    val busy: Boolean = false,
    val pending: PendingImport? = null,
    val lastSummary: ImportSummary? = null,
    val lastExportCounts: BackupCounts? = null,
    val lastExportBookmarksOnly: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val snackbarMessages = messages.receiveAsFlow()

    init {
        refreshCounts()
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            val counts = runCatching { repository.currentCounts() }.getOrNull() ?: return@launch
            _state.update { it.copy(counts = counts) }
        }
    }

    fun export(uri: Uri?, bookmarksOnly: Boolean) {
        if (uri == null) return // picker cancelled
        launchBusy {
            val counts = repository.exportToUri(uri, bookmarksOnly)
            _state.update { it.copy(lastExportCounts = counts, lastExportBookmarksOnly = bookmarksOnly) }
            messages.send(
                if (bookmarksOnly) "Exported ${counts.bookmarks} bookmark(s)" else "Backup saved (${counts.total} items)",
            )
        }
    }

    fun onImportPicked(uri: Uri?, bookmarksOnly: Boolean) {
        if (uri == null) return
        launchBusy {
            val parsed = repository.readFromUri(uri)
            val pending = PendingImport(parsed, bookmarksOnly)
            if (pending.counts.total == 0) {
                messages.send("The file contains nothing to import.")
            } else {
                _state.update { it.copy(pending = pending) }
            }
        }
    }

    fun setMode(mode: ImportMode) {
        _state.update { s -> s.copy(pending = s.pending?.copy(mode = mode)) }
    }

    fun dismissImport() {
        _state.update { it.copy(pending = null) }
    }

    fun confirmImport() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        launchBusy {
            val summary = repository.import(pending.parsed, pending.mode, pending.bookmarksOnlyRequested)
            _state.update { it.copy(lastSummary = summary) }
            refreshCounts()
            messages.send(if (summary.written.total == 0) "Nothing new to import - your data is already up to date" else "Import finished")
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                messages.send(e.message ?: "The operation failed.")
            } catch (e: Exception) {
                messages.send("The operation failed (${e.javaClass.simpleName}).")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
