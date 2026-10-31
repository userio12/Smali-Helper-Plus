package com.riyadm.smalihelperplus.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire format of the OpenAI-compatible Chat Completions API (`POST {base}/chat/completions`). */
@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChoiceDto> = emptyList(),
    val error: ApiErrorDto? = null,
)

@Serializable
data class ChoiceDto(
    val message: ResponseMessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ResponseMessageDto(val role: String? = null, val content: String? = null)

@Serializable
data class ApiErrorDto(val message: String? = null)

object ChatRole {
    const val SYSTEM = "system"
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

/** Connection settings assembled from preferences + the encrypted key. Never logged; [toString] hides the key. */
data class AiConfig(val endpoint: String, val model: String, val apiKey: String) {
    override fun toString(): String = "AiConfig(endpoint=$endpoint, model=$model, apiKey=<hidden>)"
}

/** User-presentable failure reasons; none of them contain the API key or the user's code. */
sealed class AiError(val message: String) {
    data object NotConfigured : AiError("The AI assistant is not configured. Add an endpoint, model and API key in Settings.")
    data class InsecureEndpoint(val reason: String) : AiError("The endpoint must be a valid HTTPS address ($reason).")
    data object Unauthorized : AiError("The service rejected the API key (HTTP 401/403). Check the key in Settings.")
    data object RateLimited : AiError("The service is rate limiting requests (HTTP 429). Wait a moment and try again.")
    data class BadRequest(val code: Int, val detail: String?) :
        AiError("The service rejected the request (HTTP $code)" + (detail?.let { ": $it" } ?: "."))
    data class Server(val code: Int) : AiError("The AI service had a problem (HTTP $code). Try again later.")
    data object Network : AiError("No connection to the AI service. Check your network and the endpoint address.")
    data object Timeout : AiError("The AI service took too long to answer.")
    data object InvalidResponse : AiError("The service answered, but the response was not in the expected format.")
    data object EmptyResponse : AiError("The service returned an empty answer.")
    data object TooLarge : AiError("The response was larger than the allowed size and was discarded.")
}

class AiException(val error: AiError, cause: Throwable? = null) : Exception(error.message, cause)

/** One entry of the conversation as sent to the API (system prompt is added by the client/repository). */
data class AiMessage(val role: String, val content: String)
