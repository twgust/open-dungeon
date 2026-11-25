package dev.opendungeon.core.model

/**
 * An OpenRouter API key supplied by the player (BYOK).
 *
 * Construct through [parse] so every key in the app has the documented shape:
 * `sk-or-v1-` followed by 64 lowercase hex characters.
 * See https://openrouter.ai/docs/api_reference/authentication.
 */
@JvmInline
public value class ApiKey private constructor(public val value: String) {

    /** Never leak the secret into logs, crash reports or UI state dumps. */
    override fun toString(): String = "ApiKey(${value.take(VISIBLE_PREFIX_LENGTH)}…)"

    public companion object {
        private const val VISIBLE_PREFIX_LENGTH = 12
        private val FORMAT = Regex("^sk-or-v1-[0-9a-f]{64}$")

        /** Returns a key for [raw] after trimming copy-paste whitespace, or `null` if it is not an OpenRouter key. */
        public fun parse(raw: String): ApiKey? = raw.trim().takeIf(FORMAT::matches)?.let(::ApiKey)
    }
}
