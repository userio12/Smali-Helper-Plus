package com.riyadm.smalihelperplus.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.content.ContentPackage
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.data.updates.ApplyOutcome
import com.riyadm.smalihelperplus.data.updates.AvailableUpdate
import com.riyadm.smalihelperplus.data.updates.CheckOutcome
import com.riyadm.smalihelperplus.data.updates.ContentUpdateRepository
import com.riyadm.smalihelperplus.data.updates.ManifestValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the last manual check told the user. A failed request is its own state and never reads as "up to date". */
sealed interface CheckBanner {
    data object UpToDate : CheckBanner
    data class Failed(val message: String) : CheckBanner
    data class AppUpdateRequired(val versionCode: Int) : CheckBanner
}

data class UpdatesUiState(
    val installed: List<ContentMetadataEntity> = emptyList(),
    val pending: List<AvailableUpdate> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val lastCheckMillis: Long = 0L,
    val checking: Boolean = false,
    val installing: Boolean = false,
    val restoring: Boolean = false,
    val banner: CheckBanner? = null,
    val autoCheck: Boolean = false,
    val savedManifestUrl: String = "",
    val urlDraft: String = "",
    val urlError: String? = null,
    val defaultManifestUrl: String = "",
) {
    val busy: Boolean get() = checking || installing || restoring
    val selected: List<AvailableUpdate> get() = pending.filter { it.packageId in selectedIds }
    val urlDirty: Boolean get() = urlDraft.trim() != savedManifestUrl
}

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val repository: ContentUpdateRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private data class Local(
        val checking: Boolean = false,
        val installing: Boolean = false,
        val restoring: Boolean = false,
        val banner: CheckBanner? = null,
        val deselected: Set<String> = emptySet(),
        val urlDraft: String? = null,
    )

    private val local = MutableStateFlow(Local())
    private val messages = Channel<String>(Channel.BUFFERED)
    val snackbarMessages = messages.receiveAsFlow()

    val state: StateFlow<UpdatesUiState> = combine(
        repository.installedPackages,
        repository.pendingUpdates,
        preferences.preferences,
        local,
    ) { installed, pending, prefs, l ->
        val draft = l.urlDraft ?: prefs.updateManifestUrl
        UpdatesUiState(
            installed = installed,
            pending = pending,
            selectedIds = pending.map { it.packageId }.toSet() - l.deselected,
            lastCheckMillis = prefs.lastUpdateCheckMillis,
            checking = l.checking,
            installing = l.installing,
            restoring = l.restoring,
            banner = l.banner,
            autoCheck = prefs.autoUpdateChecks,
            savedManifestUrl = prefs.updateManifestUrl,
            urlDraft = draft,
            urlError = if (draft.isBlank()) null else ManifestValidator.manifestUrlProblem(draft),
            defaultManifestUrl = com.riyadm.smalihelperplus.BuildConfig.DEFAULT_CONTENT_MANIFEST_URL,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUiState())

    fun checkNow() {
        if (state.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(checking = true, banner = null, deselected = emptySet()) }
            val banner = when (val outcome = repository.checkForUpdates()) {
                is CheckOutcome.Available -> {
                    messages.send("${outcome.updates.size} update(s) available")
                    null
                }
                is CheckOutcome.UpToDate -> CheckBanner.UpToDate
                is CheckOutcome.AppUpdateRequired -> CheckBanner.AppUpdateRequired(outcome.requiredVersionCode)
                is CheckOutcome.Failed -> CheckBanner.Failed(outcome.error.message)
            }
            local.update { it.copy(checking = false, banner = banner) }
        }
    }

    fun toggleSelected(packageId: String) {
        local.update {
            it.copy(deselected = if (packageId in it.deselected) it.deselected - packageId else it.deselected + packageId)
        }
    }

    fun installSelected() {
        val selection = state.value.selected
        if (selection.isEmpty() || state.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(installing = true) }
            val message = when (val outcome = repository.applyUpdates(selection)) {
                is ApplyOutcome.Success -> "Installed ${outcome.installed.size} update(s)"
                is ApplyOutcome.Partial -> {
                    val ok = outcome.results.count { it.success }
                    "Installed $ok package(s); then failed: ${outcome.results.first { !it.success }.error?.message}"
                }
                is ApplyOutcome.Failed -> outcome.error.message
            }
            local.update { it.copy(installing = false, deselected = emptySet()) }
            messages.send(message)
        }
    }

    fun restoreBundled() {
        if (state.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(restoring = true) }
            val message = when (val outcome = repository.restoreBundledContent()) {
                is ApplyOutcome.Success -> "Bundled content restored"
                is ApplyOutcome.Partial -> "Bundled content partly restored: ${outcome.results.first { !it.success }.error?.message}"
                is ApplyOutcome.Failed -> outcome.error.message
            }
            local.update { it.copy(restoring = false) }
            messages.send(message)
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoUpdateChecks(enabled) }
    }

    fun onUrlDraftChange(value: String) {
        local.update { it.copy(urlDraft = value) }
    }

    fun saveUrl() {
        val s = state.value
        if (s.urlError != null) return
        viewModelScope.launch {
            preferences.setUpdateManifestUrl(s.urlDraft)
            local.update { it.copy(urlDraft = null) }
            messages.send(if (s.urlDraft.isBlank()) "Using the default update source" else "Custom update source saved")
        }
    }

    fun resetUrl() {
        viewModelScope.launch {
            preferences.setUpdateManifestUrl("")
            local.update { it.copy(urlDraft = null) }
            messages.send("Using the default update source")
        }
    }
}

fun packageTitle(packageId: String): String = when (packageId) {
    ContentPackage.OPCODES -> "Opcode reference"
    ContentPackage.LESSONS -> "Lessons and flashcards"
    ContentPackage.SNIPPETS -> "Snippet library"
    else -> packageId
}
