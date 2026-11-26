package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Finding and choosing a model in the stored catalog. Searching, filtering and sorting change the list
 * at once; a sort OpenRouter has not ranked yet is fetched, and taken back if the fetch fails.
 */
public interface ModelPicker {

    public val state: StateFlow<PickerState>

    public val events: Flow<PickerEvent>

    public fun search(query: String)

    /** Sorts by OpenRouter's [sort], or its default order for `null`. */
    public fun sortBy(sort: ModelSort?)

    /** Shows only models that read [modality] as input, or stops filtering by it. */
    public fun toggleInput(modality: String)

    public fun toggleFreeOnly()

    public fun toggleTools()

    /** Shows only models with at least [tokens] of context, or stops filtering by context when it already does. */
    public fun toggleContext(tokens: Int)

    /** Fetches the catalog again. Ignored while a refresh runs. */
    public fun refresh()

    /** Makes [id] the model the player writes with. */
    public fun choose(id: ModelId)

    public fun interface Factory {
        public fun create(scope: CoroutineScope): ModelPicker
    }
}

public data class PickerState(
    val catalog: CatalogStatus = CatalogStatus.Loading,
    /** The models that pass the filters, in the chosen order. */
    val models: List<AiModel> = emptyList(),
    /** How many models the whole catalog holds. */
    val total: Int = 0,
    val filters: ModelFilters = ModelFilters(),
    val sort: ModelSort? = null,
    /** True while OpenRouter ranks the models for [sort]; the list keeps its last order meanwhile. */
    val isSorting: Boolean = false,
    val isRefreshing: Boolean = false,
    val chosen: ModelId? = null,
    /** Input modalities other than text that some model in the catalog reads, to filter by. */
    val inputModalities: List<String> = emptyList(),
)

public sealed interface CatalogStatus {
    /** No catalog is stored yet and one is being fetched. */
    public data object Loading : CatalogStatus

    /** No catalog is stored and fetching one failed. */
    public data class Missing(val error: DataError) : CatalogStatus

    public data object Ready : CatalogStatus
}

public data class ModelFilters(
    val query: String = "",
    val inputs: Set<String> = emptySet(),
    val freeOnly: Boolean = false,
    val minContext: Int? = null,
    val tools: Boolean = false,
) {
    public companion object {
        /** The context floors offered: 32K, 128K and 1M tokens. */
        public val ContextFloors: List<Int> = listOf(32_000, 128_000, 1_000_000)
    }
}

public sealed interface PickerEvent {
    /** The choice is stored. */
    public data class Chosen(val id: ModelId) : PickerEvent

    /** Something the player did could not be kept; the state already shows it taken back. */
    public data class Failed(val error: DataError) : PickerEvent
}
