package dev.opendungeon.domain.openrouter

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Every model the player can chat with using their key, with the parameters each one supports.
 *
 * The catalog lives on the device: [catalog] never waits on the network, and only [refresh] fetches.
 */
public interface ModelCatalogRepository {

    /** The stored catalog, or `null` when none was fetched for the current key. Emits on every change. */
    public val catalog: Flow<Catalog?>

    /** Fetches the whole catalog and replaces the stored one. Stored rankings are dropped with it. */
    public suspend fun refresh(): Outcome<Unit, DataError>

    /** Model ids in [sort]'s order, as OpenRouter ranks them. Stored once fetched, until the next [refresh]. */
    public suspend fun ranking(sort: ModelSort): Outcome<List<ModelId>, DataError>

    /** Every provider serving [id], fetched fresh: uptime and prices move. */
    public suspend fun endpoints(id: ModelId): Outcome<List<ModelEndpoint>, DataError>

    /** Forgets the stored catalog, for example when the key that fetched it is disconnected. */
    public suspend fun clear(): Outcome<Unit, DataError>
}

/** The catalog as last fetched: models in OpenRouter's default order. */
public data class Catalog(val models: List<AiModel>, val fetchedAt: Instant) {
    private val byId: Map<ModelId, AiModel> by lazy { models.associateBy(AiModel::id) }

    public operator fun get(id: ModelId): AiModel? = byId[id]
}
