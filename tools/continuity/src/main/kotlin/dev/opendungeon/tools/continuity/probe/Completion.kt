package dev.opendungeon.tools.continuity.probe

import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.generation.Usage

/** A whole answer from a model, as the harness needs it: no streaming, just the text and what it cost. */
internal sealed interface Completion {
    /** [cutOff] is true when the model stopped at the token limit rather than by itself. */
    data class Answered(val text: String, val usage: Usage?, val cutOff: Boolean) : Completion

    data class Failed(val error: GenerationError) : Completion
}

/** Collects [request]'s stream into one [Completion]. The stream ends with exactly one terminal event. */
internal suspend fun TextGenerator.complete(request: TextRequest): Completion {
    val text = StringBuilder()
    var completion: Completion? = null
    stream(request).collect { event ->
        when (event) {
            is TextEvent.Delta -> text.append(event.text)
            is TextEvent.Reasoning -> Unit
            is TextEvent.Completed ->
                completion =
                    Completion.Answered(text.toString().trim(), event.usage, event.cutOff)
            is TextEvent.Failed -> completion = Completion.Failed(event.error)
        }
    }
    return checkNotNull(completion) { "A text stream ended without a terminal event" }
}
