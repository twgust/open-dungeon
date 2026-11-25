package dev.opendungeon.core.network

/**
 * Optional app identification OpenRouter shows on its rankings and in the player's activity log.
 * Sent as `X-OpenRouter-Title` and `HTTP-Referer`; see https://openrouter.ai/docs/api_reference/authentication.
 */
public data class AppAttribution(
    val title: String,
    val siteUrl: String?,
)
