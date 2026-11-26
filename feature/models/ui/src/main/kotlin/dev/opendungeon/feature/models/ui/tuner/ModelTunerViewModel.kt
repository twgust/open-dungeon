package dev.opendungeon.feature.models.ui.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.models.domain.ModelTuner
import dev.opendungeon.feature.models.domain.TunerState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** A translator: intents go to the tuner as verbs, its state comes back as UI state, its failures as effects. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ModelTunerViewModel(tunerFactory: ModelTuner.Factory) :
    MviViewModel<TunerState, TunerIntent, TunerEffect>(TunerState.Loading) {

    private val tuner = tunerFactory.create(viewModelScope)

    init {
        tuner.state.onEach { state -> reduce { state } }.launchIn(viewModelScope)
        tuner.failures.onEach { sendEffect(TunerEffect.ShowFailure(it)) }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: TunerIntent) = when (intent) {
        is TunerIntent.Edit -> tuner.edit(intent.edit)
        TunerIntent.ResetAll -> tuner.resetAll()
        TunerIntent.RetryProviders -> tuner.retryProviders()
    }
}
