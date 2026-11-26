package dev.opendungeon.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal const val TITLE_HEADER = "X-OpenRouter-Title"
internal const val REFERER_HEADER = "HTTP-Referer"

/**
 * Where OpenRouter's API lives and how long calls to it may take. The REST client and the generation
 * layer both read these, so there is one address and one timeout policy.
 */
public object OpenRouter {
    /** Every endpoint is relative to this, `chat/completions` included. */
    public const val BASE_URL: String = "https://openrouter.ai/api/v1/"

    public val ConnectTimeout: Duration = 15.seconds

    /** A REST call: a catalog page or a key check. */
    public val RequestTimeout: Duration = 60.seconds

    /** Long enough for a slow model to finish a long opening; a stream is otherwise ended by its collector. */
    public val GenerationTimeout: Duration = 10.minutes

    /** A gap this long between two chunks of a stream means the provider has stalled. */
    public val StreamStallTimeout: Duration = 2.minutes
}

/** Lenient where OpenRouter's versioning policy says to be: unknown fields and enum values never fail a response. */
public val OpenRouterJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * The client every call to OpenRouter starts from: one engine, so one connection pool, the attribution
 * headers on every request, and the connect timeout. Status codes are left to the caller, so error bodies
 * can be read. Callers specialise it with [HttpClient.config], which shares the engine.
 */
public fun openRouterBaseClient(engine: HttpClientEngine, attribution: AppAttribution): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(attributionHeaders(attribution))
        install(HttpTimeout) { connectTimeoutMillis = OpenRouter.ConnectTimeout.inWholeMilliseconds }
    }

/** [this] client set up for the REST endpoints: JSON bodies, relative paths and the REST timeout. */
internal fun HttpClient.forRest(): HttpClient = config {
    install(ContentNegotiation) { json(OpenRouterJson) }
    install(HttpTimeout) { requestTimeoutMillis = OpenRouter.RequestTimeout.inWholeMilliseconds }
    defaultRequest { url(OpenRouter.BASE_URL) }
}

private fun attributionHeaders(attribution: AppAttribution) = createClientPlugin("OpenRouterAttribution") {
    onRequest { request, _ -> attribution.headers().forEach { (name, value) -> request.header(name, value) } }
}
