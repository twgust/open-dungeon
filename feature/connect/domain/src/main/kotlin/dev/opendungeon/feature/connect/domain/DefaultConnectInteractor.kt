package dev.opendungeon.feature.connect.domain

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.OpenRouterAccountRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The stored key drives the state: whenever one appears, its details and the catalog are fetched; whenever
 * it is gone, the state returns to key entry.
 *
 * Every verb is called from the screen's thread and changes the state before it returns, so the checks
 * below see each verb's effect before the next verb arrives.
 */
internal class DefaultConnectInteractor(
    private val scope: CoroutineScope,
    private val account: OpenRouterAccountRepository,
    private val catalog: ModelCatalogRepository,
) : ConnectInteractor {

    private val mutableState = MutableStateFlow<Connection>(Connection.Loading)
    override val state: StateFlow<Connection> = mutableState.asStateFlow()

    private val failureChannel = Channel<DataError>(Channel.BUFFERED)
    override val failures: Flow<DataError> = failureChannel.receiveAsFlow()

    private var loading: Job? = null

    init {
        account.isConnected
            .onEach { connected -> if (connected) load() else forget(error = null) }
            .launchIn(scope)
    }

    override fun connect(rawKey: String) {
        if (mutableState.value !is Connection.Disconnected) return
        mutableState.value = Connection.Validating
        scope.launch {
            // On success the stored key appears through isConnected, which loads the rest.
            val result = account.connect(rawKey)
            if (result is Outcome.Failure) mutableState.value = Connection.Disconnected(result.error)
        }
    }

    override fun refresh() {
        if (mutableState.value is Connection.Connected) load()
    }

    override fun disconnect() {
        val before = mutableState.value as? Connection.Connected ?: return
        forget(error = null)
        scope.launch {
            val result = account.disconnect()
            if (result is Outcome.Failure) {
                // A load the disconnect cut short is run again rather than left spinning.
                if (before.key is Remote.Loading || before.models is Remote.Loading) load() else mutableState.value = before
                failureChannel.send(result.error)
            }
        }
    }

    /** Fetches the key's details and the catalog side by side. A newer load replaces an older one. */
    private fun load() {
        loading?.cancel()
        mutableState.value = Connection.Connected(key = Remote.Loading, models = Remote.Loading)
        loading = scope.launch {
            coroutineScope {
                val key = async { account.currentKey().toRemote() }
                val models = async { catalog.fetchModels().toRemote() }
                mutableState.value = Connection.Connected(key = key.await(), models = models.await())
            }
        }
    }

    private fun forget(error: DataError?) {
        loading?.cancel()
        mutableState.value = Connection.Disconnected(error)
    }

    private fun <T> Outcome<T, DataError>.toRemote(): Remote<T> = when (this) {
        is Outcome.Success -> Remote.Loaded(value)
        is Outcome.Failure -> Remote.Failed(error)
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultConnectInteractorFactory(
    private val account: OpenRouterAccountRepository,
    private val catalog: ModelCatalogRepository,
) : ConnectInteractor.Factory {
    override fun create(scope: CoroutineScope): ConnectInteractor = DefaultConnectInteractor(scope, account, catalog)
}
