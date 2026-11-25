package dev.opendungeon.domain.generation

import dev.opendungeon.core.model.AiModel
import kotlinx.coroutines.flow.Flow

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
    val sampling: Sampling = Sampling.ProviderDefaults,
    /** Groups related requests so OpenRouter routes them to the same provider for prompt-cache hits. */
    val sessionId: String? = null,
) {
    init {
        require(messages.isNotEmpty()) { "A request needs at least one message" }
    }
}

public sealed interface TextEvent {
    public data class Delta(val text: String) : TextEvent

    /** The model finished. [cutOff] is true when it stopped at the token limit rather than by itself. */
    public data class Completed(val usage: TokenUsage?, val cutOff: Boolean) : TextEvent

    public data class Failed(val error: GenerationError) : TextEvent
}

public data class TokenUsage(val promptTokens: Int, val completionTokens: Int)
