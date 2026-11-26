package dev.opendungeon.domain.openrouter

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome

/** Every model the player can select with their key, with the parameters each one supports. */
public interface ModelCatalogRepository {
    public suspend fun fetchModels(): Outcome<List<AiModel>, DataError>
}
