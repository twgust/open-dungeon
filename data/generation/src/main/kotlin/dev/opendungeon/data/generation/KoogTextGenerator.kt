package dev.opendungeon.data.generation

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.streaming.StreamFrame
import dev.opendungeon.core.data.ApiKeyStore
import dev.opendungeon.core.model.DataError
import dev.opendungeon.data.generation.di.OpenRouterExecutor
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transformWhile

/** OpenRouter's normalised `finish_reason` values, see https://openrouter.ai/docs/api-reference/overview. */
private const val FINISH_LENGTH = "length"
private const val FINISH_CONTENT_FILTER = "content_filter"
private const val FINISH_ERROR = "error"

@Inject
@ContributesBinding(AppScope::class)
public class KoogTextGenerator(
    @OpenRouterExecutor private val executor: PromptExecutor,
    private val keys: ApiKeyStore,
) : TextGenerator {

    override fun stream(request: TextRequest): Flow<TextEvent> = flow {
        if (keys.apiKey.first() == null) {
            emit(TextEvent.Failed(GenerationError.Access(DataError.MissingApiKey)))
            return@flow
        }
        val params = request.sampling.toOpenRouterParams(request.model, request.sessionId, requireParameters = false)
        val frames = executor.executeStreaming(
            prompt = request.messages.toPrompt(id = PROMPT_ID, params = params),
            model = request.model.toLLModel(),
        )
        emitAll(frames.toTextEvents())
    }

    private companion object {
        const val PROMPT_ID = "text"
    }
}

/**
 * Text deltas pass through; the end frame becomes the single terminal event; anything else is dropped.
 * A stream that stops without an end frame reports [GenerationError.Interrupted], so collectors can
 * rely on exactly one terminal event.
 */
internal fun Flow<StreamFrame>.toTextEvents(): Flow<TextEvent> = flow {
    var finished = false
    mapNotNull { frame ->
        when (frame) {
            is StreamFrame.TextDelta -> frame.text.takeIf(String::isNotEmpty)?.let(TextEvent::Delta)
            is StreamFrame.End -> frame.toTerminalEvent()
            else -> null
        }
    }
        .catch { cause -> emit(TextEvent.Failed(cause.toGenerationError())) }
        .transformWhile { event ->
            emit(event)
            !event.isTerminal
        }
        .collect { event ->
            finished = event.isTerminal
            emit(event)
        }
    if (!finished) emit(TextEvent.Failed(GenerationError.Interrupted))
}

private val TextEvent.isTerminal: Boolean
    get() = this is TextEvent.Completed || this is TextEvent.Failed

private fun StreamFrame.End.toTerminalEvent(): TextEvent = when (finishReason) {
    FINISH_CONTENT_FILTER -> TextEvent.Failed(GenerationError.Refused)
    FINISH_ERROR -> TextEvent.Failed(GenerationError.Interrupted)
    FINISH_LENGTH -> TextEvent.Completed(usage = metaInfo.toTokenUsage(), cutOff = true)
    else -> TextEvent.Completed(usage = metaInfo.toTokenUsage(), cutOff = false)
}
