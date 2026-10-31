package com.riyadm.smalihelperplus.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import com.riyadm.smalihelperplus.data.ai.AiAssistantRepository
import com.riyadm.smalihelperplus.data.ai.AiError
import com.riyadm.smalihelperplus.data.ai.AiException
import com.riyadm.smalihelperplus.data.ai.AiMessage
import com.riyadm.smalihelperplus.data.ai.AiPrompts
import com.riyadm.smalihelperplus.data.ai.AiQuickAction
import com.riyadm.smalihelperplus.data.ai.AiSettings
import com.riyadm.smalihelperplus.data.ai.ChatRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiUiMessage(
    val id: Long,
    val role: String,
    /** What the user typed or the assistant answered (rendered in the chat). */
    val text: String,
    /** Code attached to a user message. */
    val code: String? = null,
    /** Exact content sent to the API for this message. */
    val apiContent: String = text,
    val isError: Boolean = false,
    /** True if the message (or its attachment) contains code, so a retry needs consent again. */
    val containsCode: Boolean = false,
)

/** Shown in the consent dialog before anything leaves the device. */
data class AiConsent(val host: String, val containsCode: Boolean, val codeLines: Int)

data class AiChatState(
    val settings: AiSettings = AiSettings(),
    val settingsLoaded: Boolean = false,
    val messages: List<AiUiMessage> = emptyList(),
    val attachedCode: String? = null,
    val selectedAction: AiQuickAction? = null,
    val loading: Boolean = false,
    val consent: AiConsent? = null,
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val repository: AiAssistantRepository,
    private val preferences: UserPreferencesRepository,
    private val handoff: CodeHandoff,
) : ViewModel() {

    val draft = MutableStateFlow("")

    private val messages = MutableStateFlow<List<AiUiMessage>>(emptyList())
    private val attachedCode = MutableStateFlow<String?>(null)
    private val selectedAction = MutableStateFlow<AiQuickAction?>(null)
    private val loading = MutableStateFlow(false)
    private val consent = MutableStateFlow<AiConsent?>(null)
    private val toasts = Channel<String>(Channel.BUFFERED)
    val snackbarMessages = toasts.receiveAsFlow()

    private var nextId = 1L
    private var job: Job? = null
    private var proceedAfterConsent: (() -> Unit)? = null
    private var announceCancel = true

    val state: StateFlow<AiChatState> = combine(
        repository.settings, messages, attachedCode, selectedAction, loading,
    ) { settings, msgs, code, action, isLoading ->
        AiChatState(settings, true, msgs, code, action, isLoading, null)
    }.combine(consent) { s, c -> s.copy(consent = c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiChatState())

    fun onDraftChange(value: String) {
        draft.value = value
    }

    fun selectAction(action: AiQuickAction) {
        selectedAction.value = if (selectedAction.value == action) null else action
    }

    fun attachCode(code: String) {
        val trimmed = code.trim('\n', '\r')
        when {
            trimmed.isBlank() -> toast("There is no code to attach.")
            trimmed.length > MAX_CODE_CHARS -> toast("That code is too long to send (limit ${MAX_CODE_CHARS / 1000}k characters).")
            else -> attachedCode.value = trimmed
        }
    }

    fun removeAttachment() {
        attachedCode.value = null
    }

    fun attachPlaygroundCode() {
        viewModelScope.launch {
            val code = preferences.preferences.first().lastPlaygroundCode
            if (code.isBlank()) toast("No Playground code yet. Write some in the Playground first.") else attachCode(code)
        }
    }

    fun onClipboardText(text: String?) {
        if (text.isNullOrBlank()) toast("The clipboard has no text.") else attachCode(text)
    }

    /** Takes code offered by other screens ("Ask AI"). Only consumed once the assistant is configured. */
    fun consumeHandoff() {
        handoff.consume(CodeHandoff.AI)?.let { attachCode(it.code) }
    }

    fun send() {
        if (loading.value) return
        val text = draft.value
        val code = attachedCode.value
        val action = selectedAction.value
        if (text.isBlank() && code.isNullOrBlank()) return
        val hasCode = !code.isNullOrBlank() || AiPrompts.looksLikeCode(text)
        if (action != null && action.needsCode && !hasCode) {
            toast("\"${action.label}\" needs code. Attach the Playground code or paste some first.")
            return
        }
        val apiContent = AiPrompts.buildUserMessage(action, text, code)
        val displayText = text.trim().ifEmpty { action?.label.orEmpty() }
        val message = AiUiMessage(
            id = nextId++, role = ChatRole.USER, text = displayText, code = code?.takeIf { it.isNotBlank() },
            apiContent = apiContent, containsCode = hasCode,
        )
        requestConsentThen(hasCode, code) {
            messages.update { it + message }
            draft.value = ""
            attachedCode.value = null
            selectedAction.value = null
            run()
        }
    }

    /** Re-sends the last user message after a failure or cancellation. */
    fun retry() {
        if (loading.value) return
        val current = messages.value
        val last = current.lastOrNull { it.role == ChatRole.USER } ?: return
        requestConsentThen(last.containsCode, last.code) {
            messages.update { list -> list.dropLastWhile { it.isError } }
            run()
        }
    }

    fun cancel() {
        announceCancel = true
        job?.cancel()
    }

    fun clearChat() {
        announceCancel = false
        job?.cancel()
        messages.value = emptyList()
    }

    fun confirmConsent() {
        val proceed = proceedAfterConsent
        proceedAfterConsent = null
        consent.value = null
        proceed?.invoke()
    }

    fun dismissConsent() {
        proceedAfterConsent = null
        consent.value = null
    }

    private fun requestConsentThen(containsCode: Boolean, code: String?, proceed: () -> Unit) {
        val settings = state.value.settings
        if (!settings.isConfigured) {
            toast(AiError.NotConfigured.message)
            return
        }
        // Code always needs explicit consent; plain text only when the user asked to be asked every time.
        val sendsCode = AiPrompts.requiresCodeConsent(containsCode, messages.value.filter { !it.isError }.map { it.apiContent })
        if (sendsCode || settings.alwaysAsk) {
            proceedAfterConsent = proceed
            consent.value = AiConsent(
                host = settings.host ?: "the configured server",
                containsCode = sendsCode,
                codeLines = code?.lines()?.size ?: 0,
            )
        } else {
            proceed()
        }
    }

    private fun run() {
        announceCancel = true
        loading.value = true
        job = viewModelScope.launch {
            try {
                val history = messages.value.filter { !it.isError }.map { AiMessage(it.role, it.apiContent) }
                val answer = repository.send(history)
                messages.update { it + AiUiMessage(nextId++, ChatRole.ASSISTANT, answer) }
            } catch (e: CancellationException) {
                if (announceCancel) {
                    messages.update { it + AiUiMessage(nextId++, ChatRole.ASSISTANT, "Request cancelled.", isError = true) }
                }
                throw e
            } catch (e: AiException) {
                messages.update { it + AiUiMessage(nextId++, ChatRole.ASSISTANT, e.error.message, isError = true) }
            } catch (e: Exception) {
                messages.update { it + AiUiMessage(nextId++, ChatRole.ASSISTANT, "Something went wrong (${e.javaClass.simpleName}).", isError = true) }
            } finally {
                loading.value = false
            }
        }
    }

    private fun toast(message: String) {
        toasts.trySend(message)
    }

    private companion object {
        const val MAX_CODE_CHARS = 60_000
    }
}
