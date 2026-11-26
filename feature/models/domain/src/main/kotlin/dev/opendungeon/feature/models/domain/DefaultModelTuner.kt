package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.ModelSetupRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
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
 * Edits apply to the tuning on screen at once and go to a conflated queue, so a slider dragged across
 * its range stores only where it stopped. [stored] is the last tuning known to be written: a failed write
 * puts the screen back to it.
 */
internal class DefaultModelTuner(
    private val scope: CoroutineScope,
    private val catalog: ModelCatalogRepository,
    private val setup: ModelSetupRepository,
) : ModelTuner {

    private val mutableState = MutableStateFlow<TunerState>(TunerState.Loading)
    override val state: StateFlow<TunerState> = mutableState.asStateFlow()

    private val failureChannel = Channel<DataError>(Channel.BUFFERED)
    override val failures: Flow<DataError> = failureChannel.receiveAsFlow()

    private val writes = Channel<Tuning>(Channel.CONFLATED)
    private var tuning: Tuning = Tuning.Untouched
    private var stored: Tuning = Tuning.Untouched

    init {
        scope.launch {
            val id = setup.chosenModel.first()
            val model = id?.let { catalog.catalog.first()?.get(it) }
            if (model == null) {
                mutableState.value = TunerState.NoModel
                return@launch
            }
            stored = setup.tuning(model.id).first()
            tuning = stored
            mutableState.value = TunerState.Ready(model, ParameterSheet.of(model, tuning), Providers.Loading)
            loadProviders(model)
            for (next in writes) write(model, next)
        }
    }

    override fun edit(edit: TuningEdit) {
        val ready = mutableState.value as? TunerState.Ready ?: return
        val row = ready.sheet.rows.firstOrNull { it.setting == edit.target } ?: return
        show(ready, applyEdit(tuning, edit, row))
    }

    override fun resetAll() {
        val ready = mutableState.value as? TunerState.Ready ?: return
        show(ready, Tuning.Untouched)
    }

    override fun retryProviders() {
        val ready = mutableState.value as? TunerState.Ready ?: return
        if (ready.providers is Providers.Failed) loadProviders(ready.model)
    }

    private fun show(ready: TunerState.Ready, next: Tuning) {
        if (next == tuning) return
        tuning = next
        mutableState.value = ready.copy(sheet = ParameterSheet.of(ready.model, next))
        writes.trySend(next)
    }

    private suspend fun write(model: AiModel, next: Tuning) {
        when (val result = setup.saveTuning(model.id, next)) {
            is Outcome.Success -> stored = next
            is Outcome.Failure -> {
                // A newer edit may already be queued; it supersedes this one and is written next.
                if (tuning == next) {
                    tuning = stored
                    mutableState.update {
                        (it as? TunerState.Ready)?.copy(sheet = ParameterSheet.of(model, stored))
                            ?: it
                    }
                }
                failureChannel.send(result.error)
            }
        }
    }

    private fun loadProviders(model: AiModel) {
        mutableState.update { (it as? TunerState.Ready)?.copy(providers = Providers.Loading) ?: it }
        scope.launch {
            val providers = when (val result = catalog.endpoints(model.id)) {
                is Outcome.Success -> Providers.Loaded(result.value)
                is Outcome.Failure -> Providers.Failed(result.error)
            }
            mutableState.update { (it as? TunerState.Ready)?.copy(providers = providers) ?: it }
        }
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultModelTunerFactory(
    private val catalog: ModelCatalogRepository,
    private val setup: ModelSetupRepository,
) : ModelTuner.Factory {
    override fun create(scope: CoroutineScope): ModelTuner = DefaultModelTuner(scope, catalog, setup)
}
