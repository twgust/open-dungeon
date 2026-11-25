package dev.opendungeon.core.network

/**
 * Optional app identification OpenRouter shows on its rankings and in the player's activity log.
 * Sent as `X-OpenRouter-Title` and `HTTP-Referer`; see https://openrouter.ai/docs/api_reference/authentication.
 */
public data class AppAttribution(
    val title: String,
    val siteUrl: String?,
) {
    /** The attribution as request headers, for any HTTP client that talks to OpenRouter. */
    public fun headers(): Map<String, String> = buildMap {
        put(TITLE_HEADER, title)
        siteUrl?.let { put(REFERER_HEADER, it) }
    }
}
