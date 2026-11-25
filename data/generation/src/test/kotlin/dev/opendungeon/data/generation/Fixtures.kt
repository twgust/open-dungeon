package dev.opendungeon.data.generation

import ai.koog.prompt.executor.model.PromptExecutor
import dev.opendungeon.core.data.ApiKeyStore
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.network.AppAttribution
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

internal val VALID_KEY = requireNotNull(ApiKey.parse("sk-or-v1-" + "ab".repeat(32)))

internal val ATTRIBUTION = AppAttribution(title = "Open Dungeon", siteUrl = "https://example.test")

internal class FakeApiKeyStore(initial: ApiKey? = VALID_KEY) : ApiKeyStore {
    override val apiKey = MutableStateFlow(initial)
    override suspend fun write(apiKey: ApiKey) { this.apiKey.value = apiKey }
    override suspend fun clear() { apiKey.value = null }
}

internal fun aiModel(
    id: String = "nous/hermes-4-405b",
    parameters: Set<String> = setOf("temperature", "top_p", "max_tokens", "seed"),
) = AiModel(
    id = ModelId(id),
    name = id,
    description = null,
    contextLength = 131_072,
    maxCompletionTokens = 8_192,
    pricing = ModelPricing.Metered(BigDecimal("0.000001"), BigDecimal("0.000003")),
    inputModalities = setOf("text"),
    outputModalities = setOf("text"),
    supportedParameters = parameters.mapTo(linkedSetOf(), ModelParameter::fromWireName),
    defaultParameters = DefaultParameters(),
    reasoning = null,
)

/**
 * A scripted OpenRouter on a local server, so requests run through the real OkHttp engine and Koog's
 * real SSE handling. Answers are served in order; [body] and [request] read back what was sent.
 */
internal class ScriptedOpenRouter(vararg answers: MockResponse) : AutoCloseable {
    private val server = MockWebServer().apply {
        answers.forEach(::enqueue)
        start()
    }
    private val recorded = mutableListOf<RecordedRequest>()

    val requestCount: Int
        get() = server.requestCount

    fun executor(keys: FakeApiKeyStore): PromptExecutor = openRouterPromptExecutor(
        engine = OkHttp.create(),
        attribution = ATTRIBUTION,
        apiKey = { keys.apiKey.value },
        origin = server.url("/").toString(),
    )

    fun request(index: Int): RecordedRequest {
        while (recorded.size <= index) recorded += requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request #$index" }
        return recorded[index]
    }

    fun body(index: Int): JsonObject =
        Json.parseToJsonElement(requireNotNull(request(index).body).utf8()).jsonObject

    override fun close() = server.close()
}

internal fun json(body: String, status: Int = 200): MockResponse = MockResponse.Builder()
    .code(status)
    .addHeader("Content-Type", "application/json")
    .body(body)
    .build()

internal fun eventStream(vararg events: String): MockResponse = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "text/event-stream")
    .body(events.joinToString(separator = "") { "data: $it\n\n" } + "data: [DONE]\n\n")
    .build()

internal fun chunk(content: String? = null, finishReason: String? = null, usage: String? = null): String {
    val delta = content?.let { """{"role":"assistant","content":${Json.encodeToString(it)}}""" } ?: "{}"
    val finish = finishReason?.let { "\"$it\"" } ?: "null"
    val usageField = usage?.let { ""","usage":$it""" }.orEmpty()
    return """{"id":"gen-1","object":"chat.completion.chunk","created":1764100000,"model":"nous/hermes-4-405b",""" +
        """"choices":[{"index":0,"delta":$delta,"finish_reason":$finish}]$usageField}"""
}

internal fun completion(content: String, finishReason: String = "stop"): String =
    """{"id":"gen-2","object":"chat.completion","created":1764100000,"model":"nous/hermes-4-405b",""" +
        """"choices":[{"index":0,"message":{"role":"assistant","content":${Json.encodeToString(content)}},""" +
        """"finish_reason":"$finishReason"}],"usage":{"prompt_tokens":40,"completion_tokens":12,"total_tokens":52}}"""
