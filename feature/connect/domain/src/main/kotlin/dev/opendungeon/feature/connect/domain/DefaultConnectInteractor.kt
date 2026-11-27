package dev.opendungeon.feature.connect.domain

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.ModelSetupRepository
import dev.opendungeon.domain.openrouter.OpenRouterAccountRepository
import dev.opendungeon.domain.story.StoryRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The stored key drives the link: whenever one appears, its details are fetched, and the catalog too if
 * none is stored; whenever it is gone, the state returns to key entry. The chosen model comes from the
 * stored catalog and choice, so a choice made elsewhere shows here at once. So does whether any story
 * exists, which makes home the library.
 *
 * Every verb is called from the screen's thread and changes [link] before it returns, so the checks
 * below see each verb's effect before the next verb arrives.
 */
internal class DefaultConnectInteractor(
    private val scope: CoroutineScope,
    private val account: OpenRouterAccountRepository,
    private val catalog: ModelCatalogRepository,
    private val setup: ModelSetupRepository,
    stories: StoryRepository,
) : ConnectInteractor {

    private sealed interface Link {
        data object Loading : Link

        data class Disconnected(val error: DataError?) : Link

        data object Validating : Link

        /** @property catalogFetch the catalog fetch this connection ran, if one is running or failed. */
        data class Connected(val key: Remote<KeyInfo>, val catalogFetch: Remote<Unit>?) : Link
    }

    private val link = MutableStateFlow<Link>(Link.Loading)

    private val anyStory = stories.stories.map { it.isNotEmpty() }.distinctUntilChanged()

    override val state: StateFlow<Connection> =
        combine(link, catalog.catalog, setup.chosenModel, anyStory, ::render)
            .stateIn(scope, SharingStarted.Eagerly, Connection.Loading)

    private val failureChannel = Channel<DataError>(Channel.BUFFERED)
    override val failures: Flow<DataError> = failureChannel.receiveAsFlow()

    private var loading: Job? = null

    init {
        account.isConnected
            .onEach { connected -> if (connected) load(refreshCatalog = false) else forget() }
            .launchIn(scope)
    }

    override fun connect(rawKey: String) {
        if (link.value !is Link.Disconnected) return
        link.value = Link.Validating
        scope.launch {
            // On success the stored key appears through isConnected, which loads the rest.
            val result = account.connect(rawKey)
            if (result is Outcome.Failure) link.value = Link.Disconnected(result.error)
        }
    }

    override fun refresh() {
        if (link.value is Link.Connected) load(refreshCatalog = true)
    }

    override fun disconnect() {
        val before = link.value as? Link.Connected ?: return
        forget()
        scope.launch {
            when (val result = account.disconnect()) {
                is Outcome.Success -> (catalog.clear() as? Outcome.Failure)?.let { failureChannel.send(it.error) }
                is Outcome.Failure -> {
                    // A load the disconnect cut short is run again rather than left spinning.
                    val cutShort = before.key is Remote.Loading || before.catalogFetch is Remote.Loading
                    if (cutShort) load(refreshCatalog = before.catalogFetch != null) else link.value = before
                    failureChannel.send(result.error)
                }
            }
        }
    }

    /**
     * Fetches the key's details, and the catalog beside them when asked or when none is stored. A newer
     * load replaces an older one.
     */
    private fun load(refreshCatalog: Boolean) {
        loading?.cancel()
        link.value = Link.Connected(key = Remote.Loading, catalogFetch = null)
        loading = scope.launch {
            launch {
                val key = account.currentKey().toRemote()
                link.update { (it as? Link.Connected)?.copy(key = key) ?: it }
            }
            if (refreshCatalog || catalog.catalog.first() == null) {
                link.update { (it as? Link.Connected)?.copy(catalogFetch = Remote.Loading) ?: it }
                val failure = catalog.refresh() as? Outcome.Failure
                val fetch = failure?.let { Remote.Failed(it.error) }
                link.update { (it as? Link.Connected)?.copy(catalogFetch = fetch) ?: it }
                // With a catalog to show, a failed refresh is only news.
                if (failure != null && catalog.catalog.first() != null) failureChannel.send(failure.error)
            }
        }
    }

    private fun forget() {
        loading?.cancel()
        link.value = Link.Disconnected(error = null)
    }

    private fun render(link: Link, stored: Catalog?, chosen: ModelId?, anyStory: Boolean): Connection = when (link) {
        Link.Loading -> Connection.Loading
        is Link.Disconnected -> Connection.Disconnected(link.error)
        Link.Validating -> Connection.Validating
        is Link.Connected -> Connection.Connected(
            key = link.key,
            model = choiceOf(stored, chosen, link.catalogFetch),
            showsLibrary = anyStory,
        )
    }

    private fun choiceOf(stored: Catalog?, chosen: ModelId?, fetch: Remote<Unit>?): ModelChoice = when {
        stored != null -> chosen?.let(stored::get)?.let(ModelChoice::Chosen) ?: ModelChoice.None
        fetch is Remote.Failed -> ModelChoice.Unavailable(fetch.error)
        else -> ModelChoice.Loading
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
    private val setup: ModelSetupRepository,
    private val stories: StoryRepository,
) : ConnectInteractor.Factory {
    override fun create(scope: CoroutineScope): ConnectInteractor =
        DefaultConnectInteractor(scope, account, catalog, setup, stories)
}
