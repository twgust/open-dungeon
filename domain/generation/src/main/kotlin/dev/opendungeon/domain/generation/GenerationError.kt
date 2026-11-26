package dev.opendungeon.domain.generation

import dev.opendungeon.core.model.DataError

/** Why a generation did not produce a usable result. Each case maps to something the UI can say or offer. */
public sealed interface GenerationError {
    /** The request failed before the model answered: no key, no credits, offline, rate limited, rejected. */
    public data class Access(val error: DataError) : GenerationError

    /**
     * No provider of the model accepts this request as sent, typically because no single one supports
     * every tuned parameter. Changing the tuning or the model helps; retrying does not.
     */
    public data object NoProvider : GenerationError

    /** The provider's moderation stopped the output. Another model may write it. */
    public data object Refused : GenerationError

    /**
     * The stream broke off after it started, for example a provider error mid-answer. Hitting the token
     * limit is not a failure: the text completes with [TextEvent.Completed.cutOff] set.
     */
    public data object Interrupted : GenerationError

    /** The model answered, but not in the requested structure, even after repair attempts. */
    public data class SchemaMismatch(val detail: String) : GenerationError
}
