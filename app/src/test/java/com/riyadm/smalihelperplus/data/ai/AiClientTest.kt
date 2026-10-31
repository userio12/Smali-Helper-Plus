package com.riyadm.smalihelperplus.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class AiClientTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private lateinit var server: MockWebServer
    private lateinit var client: AiClient

    // Bind to the IPv4 loopback explicitly (no localhost/IPv6 resolution) and bound every call, so a stalled
    // connection fails fast with a mapped error instead of hanging until runTest's timeout.
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    @Before fun setUp() {
        server = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }
        client = AiClient(http, json, allowCleartext = true)
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun config(base: String = server.url("/v1").toString()) = AiConfig(base, "test-model", "sk-secret-123")
    private val hello = listOf(AiMessage(ChatRole.SYSTEM, "sys"), AiMessage(ChatRole.USER, "hi"))

    private suspend fun expectError(block: suspend () -> Unit): AiError {
        val e = runCatching { block() }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiException::class.java)
        return (e as AiException).error
    }

    @Test fun doesNotRedirectPrivateConversation() = runTest {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/other")))
        server.enqueue(MockResponse().setBody("should not be reached"))
        assertThat(expectError { client.complete(config(), hello) }).isInstanceOf(AiError.BadRequest::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun sendsAuthenticatedChatCompletionsRequestAndParsesAnswer() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"x","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"  Hello there  "},"finish_reason":"stop"}],"usage":{"total_tokens":3}}""",
            ),
        )
        val answer = client.complete(config(), hello)
        assertThat(answer).isEqualTo("Hello there")

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v1/chat/completions")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-secret-123")
        assertThat(request.getHeader("Content-Type")).contains("application/json")
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("test-model")
        assertThat(body["stream"]!!.jsonPrimitive.content).isEqualTo("false")
        val messages = body["messages"] as JsonArray
        assertThat(messages).hasSize(2)
        assertThat(messages[0].jsonObject["role"]!!.jsonPrimitive.content).isEqualTo("system")
        assertThat(messages[1].jsonObject["content"]!!.jsonPrimitive.content).isEqualTo("hi")
        assertThat(body.containsKey("temperature")).isFalse()
    }

    @Test fun requestBodyIncludesMaxTokensWhenGiven() {
        val body = json.parseToJsonElement(client.buildRequestBody(" m ", hello, 8)).jsonObject as JsonObject
        assertThat(body["max_tokens"]!!.jsonPrimitive.content).isEqualTo("8")
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("m")
        assertThat(body["messages"]!!.jsonArray).hasSize(2)
    }

    @Test fun unauthorizedIsMappedAndKeyIsNeverInTheMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Incorrect API key provided: sk-secret-123"}}"""))
        val error = expectError { client.complete(config(), hello) }
        assertThat(error).isEqualTo(AiError.Unauthorized)
        assertThat(error.message).doesNotContain("sk-secret")
    }

    @Test fun rateLimitIsMapped() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        assertThat(expectError { client.complete(config(), hello) }).isEqualTo(AiError.RateLimited)
    }

    @Test fun badRequestIncludesTruncatedServerDetail() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"The model `nope` does not exist"}}"""))
        val error = expectError { client.complete(config(), hello) }
        assertThat(error).isEqualTo(AiError.BadRequest(404, "The model `nope` does not exist"))
    }

    @Test fun serverErrorIsMapped() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("oops"))
        assertThat(expectError { client.complete(config(), hello) }).isEqualTo(AiError.Server(503))
    }

    @Test fun networkFailureIsMapped() = runTest {
        val url = server.url("/v1").toString()
        server.shutdown()
        assertThat(expectError { client.complete(config(url), hello) }).isEqualTo(AiError.Network)
    }

    @Test fun malformedAndEmptyResponsesAreDistinguished() = runTest {
        server.enqueue(MockResponse().setBody("not json"))
        assertThat(expectError { client.complete(config(), hello) }).isEqualTo(AiError.InvalidResponse)
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        assertThat(expectError { client.complete(config(), hello) }).isEqualTo(AiError.InvalidResponse)
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"   "}}]}"""))
        assertThat(expectError { client.complete(config(), hello) }).isEqualTo(AiError.EmptyResponse)
    }

    @Test fun oversizedResponseIsDiscarded() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(2 * 1024 * 1024 + 100)))
        assertThat(expectError { client.complete(config(), hello) }).isEqualTo(AiError.TooLarge)
    }

    @Test fun nonHttpsEndpointIsRejectedWithoutAnyRequest() = runTest {
        val strict = AiClient(OkHttpClient(), json)
        val error = expectError { strict.complete(config(), hello) }
        assertThat(error).isInstanceOf(AiError.InsecureEndpoint::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun missingSettingsAreNotConfigured() = runTest {
        assertThat(expectError { client.complete(AiConfig("", "m", "k"), hello) }).isEqualTo(AiError.NotConfigured)
        assertThat(expectError { client.complete(AiConfig("https://x.example", "", "k"), hello) }).isEqualTo(AiError.NotConfigured)
        assertThat(expectError { client.complete(AiConfig("https://x.example", "m", ""), hello) }).isEqualTo(AiError.NotConfigured)
    }

    @Test fun configToStringHidesTheKey() {
        assertThat(config().toString()).doesNotContain("sk-secret")
    }

    @Test fun endpointNormalisation() {
        fun url(s: String) = AiClient.chatCompletionsUrl(s).toString()
        assertThat(url("https://api.example.com/v1")).isEqualTo("https://api.example.com/v1/chat/completions")
        assertThat(url("https://api.example.com/v1/")).isEqualTo("https://api.example.com/v1/chat/completions")
        assertThat(url("https://api.example.com")).isEqualTo("https://api.example.com/chat/completions")
        assertThat(url(" https://api.example.com/v1/chat/completions ")).isEqualTo("https://api.example.com/v1/chat/completions")
        assertThat(AiClient.endpointProblem("http://api.example.com/v1")).isNotNull()
        assertThat(AiClient.endpointProblem("api.example.com")).isNotNull()
        assertThat(AiClient.endpointProblem("https://u:p@api.example.com/v1")).isNotNull()
        assertThat(AiClient.endpointProblem("https://api.example.com/v1?key=1")).isNotNull()
        assertThat(AiClient.endpointProblem("https://api.example.com/v1 foo")).isNotNull()
        assertThat(AiClient.endpointProblem("https://api.example.com/v1")).isNull()
        assertThat(AiClient.hostOf("https://api.example.com/v1")).isEqualTo("api.example.com")
    }
}
