package com.riyadm.smalihelperplus.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.datastore.EditorTheme
import com.riyadm.smalihelperplus.core.datastore.ThemeMode
import com.riyadm.smalihelperplus.core.datastore.UserPreferences
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.data.ai.AiAssistantRepository
import com.riyadm.smalihelperplus.data.ai.AiClient
import com.riyadm.smalihelperplus.data.ai.AiException
import com.riyadm.smalihelperplus.data.ai.AiSettings
import com.riyadm.smalihelperplus.data.backup.UserDataMaintenance
import com.riyadm.smalihelperplus.data.updates.ContentUpdateRepository
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

sealed interface AiTestState {
    data object Idle : AiTestState
    data object Running : AiTestState
    data class Success(val host: String) : AiTestState
    data class Failure(val message: String) : AiTestState
}

data class SettingsUiState(
    val loaded: Boolean = false,
    val prefs: UserPreferences = UserPreferences(),
    val ai: AiSettings = AiSettings(),
    val pendingUpdates: Int = 0,
    val endpointDraft: String = "",
    val modelDraft: String = "",
    val apiKeyDraft: String = "",
    val endpointError: String? = null,
    val aiDirty: Boolean = false,
    val test: AiTestState = AiTestState.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val ai: AiAssistantRepository,
    private val maintenance: UserDataMaintenance,
    updates: ContentUpdateRepository,
) : ViewModel() {

    private data class Drafts(
        val endpoint: String? = null,
        val model: String? = null,
        val apiKey: String = "",
        val test: AiTestState = AiTestState.Idle,
    )

    private val drafts = MutableStateFlow(Drafts())
    private val messages = Channel<String>(Channel.BUFFERED)
    val snackbarMessages = messages.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        preferences.preferences, ai.settings, updates.pendingUpdates, drafts,
    ) { prefs, aiSettings, pending, d ->
        val endpoint = d.endpoint ?: prefs.aiEndpoint
        val model = d.model ?: prefs.aiModel
        SettingsUiState(
            loaded = true,
            prefs = prefs,
            ai = aiSettings,
            pendingUpdates = pending.size,
            endpointDraft = endpoint,
            modelDraft = model,
            apiKeyDraft = d.apiKey,
            endpointError = if (endpoint.isBlank()) null else AiClient.endpointProblem(endpoint),
            aiDirty = endpoint.trim() != prefs.aiEndpoint || model.trim() != prefs.aiModel || d.apiKey.isNotBlank(),
            test = d.test,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // ---- appearance / editor / learning -----------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { preferences.setDynamicColor(enabled) }
    fun setEditorFontSize(sp: Float) = launch { preferences.setEditorFontSize(sp) }
    fun setEditorTheme(theme: EditorTheme) = launch { preferences.setEditorTheme(theme) }
    fun setLineNumbers(enabled: Boolean) = launch { preferences.setEditorShowLineNumbers(enabled) }
    fun setWordWrap(enabled: Boolean) = launch { preferences.setEditorWordWrap(enabled) }
    fun setDailyGoal(goal: Int) = launch { preferences.setDailyGoal(goal) }
    fun setAutoUpdateChecks(enabled: Boolean) = launch { preferences.setAutoUpdateChecks(enabled) }

    // ---- AI -----------------------------------------------------------------------------------------------------

    fun setAiEnabled(enabled: Boolean) = launch { preferences.setAiEnabled(enabled) }
    fun setAiAlwaysAsk(enabled: Boolean) = launch { preferences.setAiAlwaysAsk(enabled) }
    fun onEndpointChange(value: String) = drafts.update { it.copy(endpoint = value, test = AiTestState.Idle) }
    fun onModelChange(value: String) = drafts.update { it.copy(model = value, test = AiTestState.Idle) }
    fun onApiKeyChange(value: String) = drafts.update { it.copy(apiKey = value, test = AiTestState.Idle) }

    /** Persists endpoint + model, and encrypts the key if one was typed. Nothing is sent anywhere. */
    fun saveAi() {
        val s = state.value
        if (s.endpointDraft.isNotBlank() && s.endpointError != null) {
            messages.trySend("Fix the endpoint address first.")
            return
        }
        launch {
            preferences.setAiEndpoint(s.endpointDraft)
            preferences.setAiModel(s.modelDraft)
            if (s.apiKeyDraft.isNotBlank()) ai.saveApiKey(s.apiKeyDraft)
            drafts.value = Drafts()
            messages.send("AI settings saved")
        }
    }

    fun clearApiKey() = launch {
        ai.clearApiKey()
        drafts.update { it.copy(apiKey = "", test = AiTestState.Idle) }
        messages.send("API key removed from this device")
    }

    fun testConnection() {
        val s = state.value
        if (s.test == AiTestState.Running) return
        val endpointProblem = AiClient.endpointProblem(s.endpointDraft)
        when {
            endpointProblem != null -> drafts.update { it.copy(test = AiTestState.Failure("The endpoint must be a valid HTTPS address ($endpointProblem).")) }
            s.modelDraft.isBlank() -> drafts.update { it.copy(test = AiTestState.Failure("Enter a model name first.")) }
            !s.ai.hasKey && s.apiKeyDraft.isBlank() -> drafts.update { it.copy(test = AiTestState.Failure("Enter an API key first.")) }
            else -> {
                drafts.update { it.copy(test = AiTestState.Running) }
                launch {
                    val result = ai.testConnection(s.endpointDraft, s.modelDraft, s.apiKeyDraft.takeIf { it.isNotBlank() })
                    val outcome = result.fold(
                        onSuccess = { AiTestState.Success(AiClient.hostOf(s.endpointDraft) ?: "the server") },
                        onFailure = { AiTestState.Failure((it as? AiException)?.error?.message ?: "The test failed.") },
                    )
                    drafts.update { it.copy(test = outcome) }
                }
            }
        }
    }

    // ---- data -------------------------------------------------------------------------------------------------

    fun clearSearchHistory() = launch {
        maintenance.clearSearchHistory()
        messages.send("Search history cleared")
    }

    fun clearRecentlyViewed() = launch {
        maintenance.clearRecentlyViewed()
        messages.send("Recently viewed cleared")
    }

    fun resetLearningProgress() = launch {
        maintenance.resetLearningProgress()
        messages.send("Learning progress reset")
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
