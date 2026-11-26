package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.Usage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Trying the chosen model as tuned: one prompt, one streamed reply. Leaving while a reply streams asks
 * first, since it stops the reply.
 */
public interface TryRun {

    public val state: StateFlow<TryState>

    /** Fires [TryEvent.Leave] once leaving needs nothing more from the player. */
    public val events: Flow<TryEvent>

    /** Sends [prompt] and streams the reply. Ignored while a reply streams or when [prompt] is blank. */
    public fun send(prompt: String)

    /** Stops the reply, keeping what arrived. */
    public fun stop()

    /** The player wants to leave: at once when idle, after [confirmLeave] while a reply streams. */
    public fun leave()

    public fun confirmLeave()

    /** Stays after all, letting the reply go on. */
    public fun stay()

    public fun interface Factory {
        public fun create(scope: CoroutineScope): TryRun
    }
}

public data class TryState(
    /** The chosen model; `null` while loading or when none is chosen, see [noModel]. */
    val model: AiModel? = null,
    val noModel: Boolean = false,
    val reply: String = "",
    val reasoning: String = "",
    val status: RunStatus = RunStatus.Idle,
    val confirmingLeave: Boolean = false,
)

public sealed interface RunStatus {
    public data object Idle : RunStatus

    public data object Streaming : RunStatus

    /** The player stopped the reply. */
    public data object Stopped : RunStatus

    /** @property cutOff true when the model stopped at the token limit. */
    public data class Done(val usage: Usage?, val cutOff: Boolean) : RunStatus

    public data class Failed(val error: GenerationError) : RunStatus
}

public sealed interface TryEvent {
    public data object Leave : TryEvent
}
