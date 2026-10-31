package com.riyadm.smalihelperplus.data.ai

import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Current AI configuration as shown in Settings and the assistant. The key itself is never exposed. */
data class AiSettings(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val model: String = "",
    val hasKey: Boolean = false,
    val alwaysAsk: Boolean = true,
) {
    val isConfigured: Boolean
        get() = enabled && model.isNotBlank() && hasKey && AiClient.endpointProblem(endpoint) == null
    val host: String? get() = AiClient.hostOf(endpoint)
}

@Singleton
class AiAssistantRepository @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val keyStore: ApiKeyStore,
    private val client: AiClient,
) {
    val settings: Flow<AiSettings> = combine(preferences.preferences, keyStore.hasKey) { p, hasKey ->
        AiSettings(p.aiEnabled, p.aiEndpoint, p.aiModel, hasKey, p.aiAlwaysAskBeforeSending)
    }

    suspend fun saveApiKey(key: String) = withContext(Dispatchers.IO) { keyStore.save(key) }
    suspend fun clearApiKey() = withContext(Dispatchers.IO) { keyStore.clear() }

    /** Sends the conversation (system prompt is prepended). Only the most recent messages are sent. */
    suspend fun send(history: List<AiMessage>): String {
        val s = settings.first()
        if (!s.isConfigured) throw AiException(AiError.NotConfigured)
        val key = withContext(Dispatchers.IO) { keyStore.load() } ?: throw AiException(AiError.NotConfigured)
        val payload = listOf(AiMessage(ChatRole.SYSTEM, AiPrompts.SYSTEM_PROMPT)) + trimHistory(history)
        return client.complete(AiConfig(s.endpoint, s.model, key), payload)
    }

    /**
     * Checks that the service accepts the given settings with a tiny prompt containing no user data.
     * [apiKeyOverride] lets the Settings screen test a key before saving it.
     */
    suspend fun testConnection(endpoint: String, model: String, apiKeyOverride: String?): Result<Unit> = try {
        val key = apiKeyOverride?.takeIf { it.isNotBlank() } ?: withContext(Dispatchers.IO) { keyStore.load() } ?: throw AiException(AiError.NotConfigured)
        client.complete(
            AiConfig(endpoint, model, key.trim()),
            listOf(AiMessage(ChatRole.USER, "Reply with the single word: ok")),
            maxTokens = 8,
        )
        Result.success(Unit)
    } catch (e: AiException) {
        Result.failure(e)
    }

    companion object {
        const val MAX_HISTORY_MESSAGES = 12
        const val MAX_HISTORY_CHARS = 60_000

        /** Keeps the newest messages that fit the limits (the latest message is always kept). */
        fun trimHistory(history: List<AiMessage>): List<AiMessage> {
            val kept = ArrayDeque<AiMessage>()
            var chars = 0
            for (m in history.asReversed()) {
                if (kept.size >= MAX_HISTORY_MESSAGES) break
                if (kept.isNotEmpty() && chars + m.content.length > MAX_HISTORY_CHARS) break
                kept.addFirst(m)
                chars += m.content.length
            }
            // Conversations should start with a user turn.
            while (kept.size > 1 && kept.first().role != ChatRole.USER) kept.removeFirst()
            return kept.toList()
        }
    }
}
