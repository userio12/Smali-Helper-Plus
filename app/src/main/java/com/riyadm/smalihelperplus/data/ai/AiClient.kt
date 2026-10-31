package com.riyadm.smalihelperplus.data.ai

import com.riyadm.smalihelperplus.data.net.await
import com.riyadm.smalihelperplus.data.net.readBytesLimited
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Minimal OpenAI-compatible Chat Completions client. HTTPS only, no redirects across schemes, bounded response size,
 * long read timeout (models can be slow). Nothing is logged.
 *
 * [allowCleartext] exists only so unit tests can use a plain-HTTP MockWebServer; the Hilt constructor passes false.
 */
class AiClient internal constructor(
    client: OkHttpClient,
    private val json: Json,
    private val allowCleartext: Boolean,
) {
    @Inject constructor(client: OkHttpClient, json: Json) : this(client, json, false)

    private val http: OkHttpClient = client.newBuilder()
        .followSslRedirects(false)
        .followRedirects(false)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Sends [messages] (system prompt included by the caller) and returns the assistant's text. */
    suspend fun complete(config: AiConfig, messages: List<AiMessage>, maxTokens: Int? = null): String = withContext(Dispatchers.IO) {
        if (config.endpoint.isBlank() || config.model.isBlank() || config.apiKey.isBlank()) throw AiException(AiError.NotConfigured)
        val url = chatCompletionsUrl(config.endpoint, allowCleartext)
        val body = buildRequestBody(config.model, messages, maxTokens)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            http.newCall(request).await().use { response ->
                val bytes = response.body.source().readBytesLimited(MAX_RESPONSE_BYTES)
                    ?: throw AiException(AiError.TooLarge)
                val text = bytes.toString(Charsets.UTF_8)
                if (!response.isSuccessful) throw AiException(mapHttpError(response.code, text))
                return@withContext parseResponse(text)
            }
        } catch (e: InterruptedIOException) {
            throw AiException(AiError.Timeout, e)
        } catch (e: IOException) {
            throw AiException(AiError.Network, e)
        }
    }

    internal fun buildRequestBody(model: String, messages: List<AiMessage>, maxTokens: Int?): String =
        json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = model.trim(),
                messages = messages.map { ChatMessageDto(it.role, it.content) },
                maxTokens = maxTokens,
            ),
        )

    internal fun parseResponse(text: String): String {
        val parsed = try {
            json.decodeFromString(ChatCompletionResponse.serializer(), text)
        } catch (e: SerializationException) {
            throw AiException(AiError.InvalidResponse, e)
        } catch (e: IllegalArgumentException) {
            throw AiException(AiError.InvalidResponse, e)
        }
        val content = parsed.choices.firstOrNull()?.message?.content
        if (parsed.choices.isEmpty() && parsed.error != null) throw AiException(AiError.InvalidResponse)
        if (content == null) throw AiException(AiError.InvalidResponse)
        if (content.isBlank()) throw AiException(AiError.EmptyResponse)
        return content.trim()
    }

    internal fun mapHttpError(code: Int, body: String): AiError = when (code) {
        401, 403 -> AiError.Unauthorized
        429 -> AiError.RateLimited
        in 500..599 -> AiError.Server(code)
        else -> AiError.BadRequest(code, if (code in 400..499) errorDetail(body) else null)
    }

    private fun errorDetail(body: String): String? = try {
        json.decodeFromString(ChatCompletionResponse.serializer(), body).error?.message
            ?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_DETAIL_CHARS)?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
        private const val MAX_DETAIL_CHARS = 200

        /** Appends `/chat/completions` to the user-provided base URL (unless it is already there). */
        fun chatCompletionsUrl(endpoint: String, allowCleartext: Boolean = false): HttpUrl {
            val trimmed = endpoint.trim()
            // OkHttp silently percent-encodes inner whitespace; an address with spaces is almost certainly a typo.
            if (trimmed.any { it.isWhitespace() }) throw AiException(AiError.InsecureEndpoint("remove spaces from the address"))
            val parsed = trimmed.toHttpUrlOrNull()
                ?: throw AiException(AiError.InsecureEndpoint("not a valid web address"))
            if (!parsed.isHttps && !allowCleartext) throw AiException(AiError.InsecureEndpoint("it must start with https://"))
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
                throw AiException(AiError.InsecureEndpoint("credentials in the address are not allowed"))
            }
            if (parsed.query != null || parsed.fragment != null) {
                throw AiException(AiError.InsecureEndpoint("remove the query or fragment"))
            }
            val segments = parsed.pathSegments.filter { it.isNotEmpty() }
            if (segments.size >= 2 && segments[segments.size - 2] == "chat" && segments.last() == "completions") return parsed
            return parsed.newBuilder().apply {
                encodedPath("/")
                segments.forEach { addPathSegment(it) }
                addPathSegments("chat/completions")
            }.build()
        }

        /** Returns null if [endpoint] is usable, otherwise a short reason (for the Settings field). */
        fun endpointProblem(endpoint: String): String? = try {
            chatCompletionsUrl(endpoint)
            null
        } catch (e: AiException) {
            (e.error as? AiError.InsecureEndpoint)?.reason ?: "invalid address"
        }

        /** Host shown in consent dialogs. */
        fun hostOf(endpoint: String): String? = endpoint.trim().toHttpUrlOrNull()?.host
    }
}
