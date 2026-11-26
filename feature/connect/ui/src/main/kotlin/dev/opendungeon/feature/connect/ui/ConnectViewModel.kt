package dev.opendungeon.feature.connect.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.feature.connect.domain.ConnectInteractor
import dev.opendungeon.feature.connect.domain.Connection
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** A translator: intents go to the interactor as verbs, its state comes back as UI state, its failures as effects. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ConnectViewModel(interactorFactory: ConnectInteractor.Factory) :
    MviViewModel<Connection, ConnectIntent, ConnectEffect>(Connection.Loading) {

    private val interactor = interactorFactory.create(viewModelScope)

    init {
        interactor.state.onEach { connection -> reduce { connection } }.launchIn(viewModelScope)
        interactor.failures.onEach { sendEffect(ConnectEffect.ShowFailure(it)) }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ConnectIntent) = when (intent) {
        is ConnectIntent.Submit -> interactor.connect(intent.rawKey)
        ConnectIntent.Refresh -> interactor.refresh()
        ConnectIntent.Disconnect -> interactor.disconnect()
    }
}
