package dev.opendungeon.data.generation

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.network.AppAttribution
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal const val OPENROUTER_ORIGIN: String = "https://openrouter.ai"
private const val CLIENT_NAME = "OpenRouter"

private val CONNECT_TIMEOUT = 15.seconds

/** Long enough for a slow model to finish a long opening; the stream itself is cancelled by its collector. */
private val REQUEST_TIMEOUT = 10.minutes

/** A gap this long between two stream chunks means the provider has stalled. */
private val SOCKET_TIMEOUT = 2.minutes

/**
 * Lenient like the rest of the app's OpenRouter parsing: OpenRouter adds response fields without notice
 * (https://openrouter.ai/docs/api_reference/versioning).
 */
internal val OpenRouterKoogJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Builds Koog's executor for OpenRouter on top of a Ktor [engine].
 *
 * The key is read per request through [apiKey] rather than baked into the client, so connecting a new
 * key or disconnecting takes effect on the next request without rebuilding anything.
 */
internal fun openRouterPromptExecutor(
    engine: HttpClientEngine,
    attribution: AppAttribution,
    apiKey: suspend () -> ApiKey?,
    origin: String = OPENROUTER_ORIGIN,
): PromptExecutor {
    val authenticated = HttpClient(engine) {
        expectSuccess = false
        install(openRouterRequestHeaders(attribution, apiKey))
    }
    val httpClient = KtorKoogHttpClient.Factory(baseClient = authenticated).create(
        clientName = CLIENT_NAME,
        baseUrl = origin,
        headers = emptyMap(),
        queryParameters = emptyMap(),
        requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds,
        connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds,
        socketTimeoutMillis = SOCKET_TIMEOUT.inWholeMilliseconds,
        json = OpenRouterKoogJson,
    )
    val client = OpenRouterLLMClient(settings = OpenRouterClientSettings(baseUrl = origin), httpClient = httpClient)
    return MultiLLMPromptExecutor(mapOf(LLMProvider.OpenRouter to client))
}

private fun openRouterRequestHeaders(attribution: AppAttribution, apiKey: suspend () -> ApiKey?) =
    createClientPlugin("OpenRouterRequestHeaders") {
        onRequest { request, _ ->
            attribution.headers().forEach { (name, value) -> request.header(name, value) }
            apiKey()?.let { request.bearerAuth(it.value) }
        }
    }
