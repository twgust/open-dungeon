package dev.opendungeon.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.map
import dev.opendungeon.core.mvi.MviViewModel
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.OpenRouterAccountRepository
import dev.opendungeon.feature.connect.ConnectState.Phase
import dev.opendungeon.feature.connect.ConnectState.Remote
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The stored key drives the screen: whenever it appears, key details and the model catalog are
 * fetched; whenever it is cleared, the screen returns to key entry.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ConnectViewModel(
    private val account: OpenRouterAccountRepository,
    private val catalog: ModelCatalogRepository,
) : MviViewModel<ConnectState, ConnectIntent, Nothing>(ConnectState()) {

    private var refreshJob: Job? = null

    init {
        account.isConnected
            .onEach { connected -> if (connected) refresh() else reduce { copy(phase = Phase.Disconnected()) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ConnectIntent) {
        when (intent) {
            is ConnectIntent.Submit -> submit(intent.rawKey)
            ConnectIntent.Refresh -> refresh()
            ConnectIntent.Disconnect -> viewModelScope.launch { account.disconnect() }
        }
    }

    private fun submit(rawKey: String) {
        if (currentState.phase == Phase.Validating) return
        reduce { copy(phase = Phase.Validating) }
        viewModelScope.launch {
            // On success the stored key emits through isConnected, which loads the catalog.
            val result = account.connect(rawKey)
            if (result is Outcome.Failure) reduce { copy(phase = Phase.Disconnected(result.error)) }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        reduce { copy(phase = Phase.Connected(key = Remote.Loading, models = Remote.Loading)) }
        refreshJob = viewModelScope.launch {
            coroutineScope {
                val key = async { account.currentKey().toRemote() }
                val models = async { catalog.fetchModels().map { it.toImmutableList() }.toRemote() }
                val connected = Phase.Connected(key = key.await(), models = models.await())
                reduce { copy(phase = connected) }
            }
        }
    }

    private fun <T> Outcome<T, DataError>.toRemote(): Remote<T> = when (this) {
        is Outcome.Success -> Remote.Loaded(value)
        is Outcome.Failure -> Remote.Failed(error)
    }
}
