package dev.opendungeon.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

internal const val TITLE_HEADER = "X-OpenRouter-Title"
internal const val REFERER_HEADER = "HTTP-Referer"

/**
 * Builds the Ktor client for OpenRouter. Status codes are checked by [safeRequest], not by Ktor,
 * so `expectSuccess` stays off and error bodies can be decoded.
 */
internal fun openRouterHttpClient(
    engine: HttpClientEngine,
    json: Json,
    attribution: AppAttribution,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        connectTimeoutMillis = 15.seconds.inWholeMilliseconds
        requestTimeoutMillis = 60.seconds.inWholeMilliseconds
    }
    defaultRequest {
        url(OpenRouterApi.BASE_URL)
        attribution.headers().forEach(headers::append)
    }
}

/** Lenient where OpenRouter's versioning policy says to be: unknown fields and enum values never fail a response. */
internal fun openRouterJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
