package dev.opendungeon.feature.models.ui.trial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.models.domain.TryEvent
import dev.opendungeon.feature.models.domain.TryRun
import dev.opendungeon.feature.models.domain.TryState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A translator: intents go to the run as verbs, its state comes back as UI state, its events as effects.
 * The run lives in this ViewModel's scope, so a reply streams on through rotation and stops when the
 * screen leaves the back stack.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class TryViewModel(runFactory: TryRun.Factory) : MviViewModel<TryState, TryIntent, TryEffect>(TryState()) {

    private val run = runFactory.create(viewModelScope)

    init {
        run.state.onEach { state -> reduce { state } }.launchIn(viewModelScope)
        run.events.onEach { event ->
            when (event) {
                TryEvent.Leave -> sendEffect(TryEffect.Leave)
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: TryIntent) = when (intent) {
        is TryIntent.Send -> run.send(intent.prompt)
        TryIntent.Stop -> run.stop()
        TryIntent.Leave -> run.leave()
        TryIntent.ConfirmLeave -> run.confirmLeave()
        TryIntent.Stay -> run.stay()
    }
}
