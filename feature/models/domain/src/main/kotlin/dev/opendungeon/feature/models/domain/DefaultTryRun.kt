package dev.opendungeon.feature.models.domain

import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.ModelSetupRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The run is one job collecting the generator's flow. Cancelling it closes the connection, which is how
 * OpenRouter is told to stop: the provider stops generating, and billing with it where it supports that.
 */
internal class DefaultTryRun(
    private val scope: CoroutineScope,
    private val catalog: ModelCatalogRepository,
    private val setup: ModelSetupRepository,
    private val generator: TextGenerator,
) : TryRun {

    private val mutableState = MutableStateFlow(TryState())
    override val state: StateFlow<TryState> = mutableState.asStateFlow()

    private val eventChannel = Channel<TryEvent>(Channel.BUFFERED)
    override val events: Flow<TryEvent> = eventChannel.receiveAsFlow()

    private var run: Job? = null

    init {
        scope.launch {
            val model = setup.chosenModel.first()?.let { id -> catalog.catalog.first()?.get(id) }
            mutableState.update { it.copy(model = model, noModel = model == null) }
        }
    }

    override fun send(prompt: String) {
        val current = mutableState.value
        val model = current.model ?: return
        if (current.status == RunStatus.Streaming || prompt.isBlank()) return
        mutableState.value = current.copy(reply = "", reasoning = "", status = RunStatus.Streaming)
        run = scope.launch {
            // Read when sent, so the run uses the tuning as it is now.
            val tuning = setup.tuning(model.id).first()
            val request =
                TextRequest(model = model, messages = listOf(ChatMessage.User(prompt.trim())), tuning = tuning)
            generator.stream(request).collect { event ->
                mutableState.update { it.after(event) }
            }
        }
    }

    override fun stop() {
        if (mutableState.value.status != RunStatus.Streaming) return
        run?.cancel()
        mutableState.update { it.copy(status = RunStatus.Stopped) }
    }

    override fun leave() {
        if (mutableState.value.status == RunStatus.Streaming) {
            mutableState.update { it.copy(confirmingLeave = true) }
        } else {
            eventChannel.trySend(TryEvent.Leave)
        }
    }

    override fun confirmLeave() {
        if (!mutableState.value.confirmingLeave) return
        run?.cancel()
        mutableState.update {
            it.copy(
                confirmingLeave = false,
                status = if (it.status ==
                    RunStatus.Streaming
                ) {
                    RunStatus.Stopped
                } else {
                    it.status
                },
            )
        }
        eventChannel.trySend(TryEvent.Leave)
    }

    override fun stay() = mutableState.update { it.copy(confirmingLeave = false) }

    private fun TryState.after(event: TextEvent): TryState = when (event) {
        is TextEvent.Delta -> copy(reply = reply + event.text)
        is TextEvent.Reasoning -> copy(reasoning = reasoning + event.text)
        is TextEvent.Completed -> copy(status = RunStatus.Done(event.usage, event.cutOff))
        is TextEvent.Failed -> copy(status = RunStatus.Failed(event.error))
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultTryRunFactory(
    private val catalog: ModelCatalogRepository,
    private val setup: ModelSetupRepository,
    private val generator: TextGenerator,
) : TryRun.Factory {
    override fun create(scope: CoroutineScope): TryRun = DefaultTryRun(scope, catalog, setup, generator)
}
