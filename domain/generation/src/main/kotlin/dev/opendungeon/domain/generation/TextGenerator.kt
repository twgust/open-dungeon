package dev.opendungeon.domain.generation

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.Tuning
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** Streams free text from a model. */
public interface TextGenerator {
    /**
     * A cold flow: nothing is requested until it is collected, and cancelling the collector cancels
     * the request. The flow always ends with exactly one [TextEvent.Completed] or [TextEvent.Failed];
     * expected failures never surface as exceptions.
     */
    public fun stream(request: TextRequest): Flow<TextEvent>
}

public data class TextRequest(
    val model: AiModel,
    val messages: List<ChatMessage>,
    /** Only values for parameters the model lists are sent; see [Tuning]. */
    val tuning: Tuning = Tuning.Untouched,
    /** Groups related requests so OpenRouter routes them to the same provider for prompt-cache hits. */
    val sessionId: String? = null,
) {
    init {
        require(messages.isNotEmpty()) { "A request needs at least one message" }
    }
}

public sealed interface TextEvent {
    public data class Delta(val text: String) : TextEvent

    /** Part of the model's reasoning, streamed before its answer by models that think aloud. */
    public data class Reasoning(val text: String) : TextEvent

    /** The model finished. [cutOff] is true when it stopped at the token limit rather than by itself. */
    public data class Completed(val usage: Usage?, val cutOff: Boolean) : TextEvent

    public data class Failed(val error: GenerationError) : TextEvent
}

/**
 * What a request used, as OpenRouter accounted it.
 *
 * @property reasoningTokens the part of [completionTokens] spent reasoning, when the provider reports it.
 * @property cost what OpenRouter charged, in USD; `null` when it did not say.
 */
public data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val reasoningTokens: Int? = null,
    val cost: BigDecimal? = null,
)
