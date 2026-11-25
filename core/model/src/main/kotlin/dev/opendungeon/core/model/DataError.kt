package dev.opendungeon.core.model

/** Failures the data layer reports to features. Each case maps to something the UI can say or do. */
public sealed interface DataError {
    /** No key is stored; the player has to connect OpenRouter first. */
    public data object MissingApiKey : DataError

    /** The text entered does not have the OpenRouter key format. */
    public data object MalformedApiKey : DataError

    /** OpenRouter rejected the key (deleted, disabled or expired). */
    public data class Unauthorized(val message: String) : DataError

    /** The key's credit limit or the account balance is exhausted. */
    public data class InsufficientCredits(val message: String) : DataError

    public data class RateLimited(val message: String) : DataError

    /** No connection, DNS failure, timeout. Retrying may help. */
    public data object Network : DataError

    /** Any other non-success response from OpenRouter. */
    public data class Remote(val status: Int, val message: String) : DataError

    /** The response did not match the documented schema. */
    public data object UnexpectedResponse : DataError
}
