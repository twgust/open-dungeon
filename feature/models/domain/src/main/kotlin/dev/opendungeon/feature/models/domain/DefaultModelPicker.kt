package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.openrouter.Catalog
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A stored catalog older than this is fetched again when the picker opens. */
internal val CatalogMaxAge = 1.days

/**
 * Everything the player set lives in [local]; the list is derived from it, the stored catalog and the
 * stored choice, so a verb's effect shows as soon as [local] changes.
 */
internal class DefaultModelPicker(
    private val scope: CoroutineScope,
    private val catalog: ModelCatalogRepository,
    private val setup: ModelSetupRepository,
    private val clock: Clock,
) : ModelPicker {

    private data class Local(
        val filters: ModelFilters = ModelFilters(),
        val sort: ModelSort? = null,
        /** The last sort whose ranking was at hand: where a failed sort goes back to. */
        val appliedSort: ModelSort? = null,
        val rankings: Map<ModelSort, List<ModelId>> = emptyMap(),
        /** The catalog fetch the [rankings] were made from. */
        val rankedAt: Instant? = null,
        val isRefreshing: Boolean = false,
        val refreshError: DataError? = null,
        /** A choice shown before it is stored. */
        val pendingChoice: ModelId? = null,
    ) {
        val isSorting: Boolean get() = sort != null && sort !in rankings
    }

    private val local = MutableStateFlow(Local())

    private val eventChannel = Channel<PickerEvent>(Channel.BUFFERED)
    override val events: Flow<PickerEvent> = eventChannel.receiveAsFlow()

    override val state: StateFlow<PickerState> =
        combine(catalog.catalog, setup.chosenModel, local) { stored, chosen, local -> render(stored, chosen, local) }
            .stateIn(scope, SharingStarted.Eagerly, PickerState())

    private var rankingJob: Job? = null
    private var refreshJob: Job? = null

    init {
        // Rankings belong to one fetch of the catalog; a new fetch drops them and ranks the current sort again.
        catalog.catalog.map { it?.fetchedAt }.distinctUntilChanged().onEach { fetchedAt ->
            local.update { it.copy(rankings = emptyMap(), rankedAt = fetchedAt) }
            local.value.sort?.let(::rank)
        }.launchIn(scope)
        scope.launch {
            val stored = catalog.catalog.first()
            if (stored == null || clock.now() - stored.fetchedAt > CatalogMaxAge) refresh()
        }
    }

    override fun search(query: String) = local.update { it.copy(filters = it.filters.copy(query = query)) }

    override fun sortBy(sort: ModelSort?) {
        if (sort == local.value.sort) return
        rankingJob?.cancel()
        local.update {
            it.copy(
                sort = sort,
                appliedSort = if (sort == null ||
                    sort in it.rankings
                ) {
                    sort
                } else {
                    it.appliedSort
                },
            )
        }
        sort?.let(::rank)
    }

    override fun toggleInput(modality: String) = local.update {
        val inputs = it.filters.inputs
        it.copy(filters = it.filters.copy(inputs = if (modality in inputs) inputs - modality else inputs + modality))
    }

    override fun toggleFreeOnly() = local.update { it.copy(filters = it.filters.copy(freeOnly = !it.filters.freeOnly)) }

    override fun toggleTools() = local.update { it.copy(filters = it.filters.copy(tools = !it.filters.tools)) }

    override fun toggleContext(tokens: Int) = local.update {
        it.copy(filters = it.filters.copy(minContext = if (it.filters.minContext == tokens) null else tokens))
    }

    override fun refresh() {
        if (refreshJob?.isActive == true) return
        local.update { it.copy(isRefreshing = true, refreshError = null) }
        refreshJob = scope.launch {
            val result = catalog.refresh()
            val error = (result as? Outcome.Failure)?.error
            local.update { it.copy(isRefreshing = false, refreshError = error) }
            // With a catalog on screen, a failed refresh is only news; without one, the screen shows it.
            if (error != null && catalog.catalog.first() != null) eventChannel.send(PickerEvent.Failed(error))
        }
    }

    override fun choose(id: ModelId) {
        local.update { it.copy(pendingChoice = id) }
        scope.launch {
            val result = setup.choose(id)
            local.update { if (it.pendingChoice == id) it.copy(pendingChoice = null) else it }
            when (result) {
                is Outcome.Success -> eventChannel.send(PickerEvent.Chosen(id))
                is Outcome.Failure -> eventChannel.send(PickerEvent.Failed(result.error))
            }
        }
    }

    /** Fetches [sort]'s ranking unless it is at hand. A failure puts the last working sort back. */
    private fun rank(sort: ModelSort) {
        if (sort in local.value.rankings) return
        val rankedAt = local.value.rankedAt
        rankingJob?.cancel()
        rankingJob = scope.launch {
            when (val result = catalog.ranking(sort)) {
                is Outcome.Success -> local.update {
                    if (it.rankedAt != rankedAt) return@update it
                    it.copy(
                        rankings = it.rankings + (sort to result.value),
                        appliedSort = if (it.sort == sort) sort else it.appliedSort,
                    )
                }
                is Outcome.Failure -> {
                    local.update { if (it.sort == sort) it.copy(sort = it.appliedSort) else it }
                    eventChannel.send(PickerEvent.Failed(result.error))
                }
            }
        }
    }

    private fun render(stored: Catalog?, chosen: ModelId?, local: Local): PickerState {
        val status = when {
            stored != null -> CatalogStatus.Ready
            local.refreshError != null -> CatalogStatus.Missing(local.refreshError)
            else -> CatalogStatus.Loading
        }
        val all = stored?.models.orEmpty()
        val order = (if (local.isSorting) local.appliedSort else local.sort)?.let(local.rankings::get)
        val ordered = order?.let(all::rankedBy) ?: all
        return PickerState(
            catalog = status,
            models = ordered.matching(local.filters),
            total = all.size,
            filters = local.filters,
            sort = local.sort,
            isSorting = local.isSorting,
            isRefreshing = local.isRefreshing,
            chosen = local.pendingChoice ?: chosen,
            inputModalities = all.inputModalities(),
        )
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultModelPickerFactory(
    private val catalog: ModelCatalogRepository,
    private val setup: ModelSetupRepository,
    private val clock: Clock,
) : ModelPicker.Factory {
    override fun create(scope: CoroutineScope): ModelPicker = DefaultModelPicker(scope, catalog, setup, clock)
}
