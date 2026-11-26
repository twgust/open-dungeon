package dev.opendungeon.data.generation

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.network.OpenRouter
import dev.opendungeon.core.network.OpenRouterJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.bearerAuth

private const val CLIENT_NAME = "OpenRouter"

/**
 * Koog's executor for OpenRouter, built on the app's one OpenRouter client ([baseClient]), so generation
 * shares its engine, attribution and address with every other call.
 *
 * The key is read per request through [apiKey] rather than baked into the client, so connecting a new
 * key or disconnecting takes effect on the next request without rebuilding anything.
 *
 * @param baseUrl the API root every Koog path is resolved against; tests point it at a local server.
 */
internal fun openRouterPromptExecutor(
    baseClient: HttpClient,
    apiKey: suspend () -> ApiKey?,
    baseUrl: String = OpenRouter.BASE_URL,
): PromptExecutor {
    val authenticated = baseClient.config { install(bearerFromStore(apiKey)) }
    val httpClient = KtorKoogHttpClient.Factory(baseClient = authenticated).create(
        clientName = CLIENT_NAME,
        baseUrl = baseUrl,
        headers = emptyMap(),
        queryParameters = emptyMap(),
        requestTimeoutMillis = OpenRouter.GenerationTimeout.inWholeMilliseconds,
        connectTimeoutMillis = OpenRouter.ConnectTimeout.inWholeMilliseconds,
        socketTimeoutMillis = OpenRouter.StreamStallTimeout.inWholeMilliseconds,
        json = OpenRouterJson,
    )
    // Koog's default paths carry the "api/v1/" prefix; ours are relative to the shared base URL instead.
    val settings = OpenRouterClientSettings(
        baseUrl = baseUrl,
        chatCompletionsPath = "chat/completions",
        modelsPath = "models",
        embeddingsPath = "embeddings",
    )
    val client = OpenRouterLLMClient(settings = settings, httpClient = httpClient)
    return MultiLLMPromptExecutor(mapOf(LLMProvider.OpenRouter to client))
}

private fun bearerFromStore(apiKey: suspend () -> ApiKey?) = createClientPlugin("OpenRouterBearer") {
    onRequest { request, _ -> apiKey()?.let { request.bearerAuth(it.value) } }
}
