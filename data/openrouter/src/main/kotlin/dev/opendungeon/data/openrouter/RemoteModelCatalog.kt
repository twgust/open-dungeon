package dev.opendungeon.data.openrouter

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome

/**
 * The catalog as OpenRouter serves it to the stored key, straight from the network. The data layer's
 * cache sits in front of it; features never call it.
 */
public interface RemoteModelCatalog {

    /** Every model this key can chat with, in [sort]'s order, or OpenRouter's default order for `null`. */
    public suspend fun fetchModels(sort: ModelSort?): Outcome<List<AiModel>, DataError>

    /** Every provider serving [id]. */
    public suspend fun fetchEndpoints(id: ModelId): Outcome<List<ModelEndpoint>, DataError>
}
