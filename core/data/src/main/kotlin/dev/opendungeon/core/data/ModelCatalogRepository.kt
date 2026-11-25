package dev.opendungeon.core.data

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.OpenRouterApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Every model the player can select with their key, with the parameters each one supports. */
public interface ModelCatalogRepository {
    public suspend fun fetchModels(): Outcome<List<AiModel>, DataError>
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultModelCatalogRepository(
    private val api: OpenRouterApi,
    private val store: ApiKeyStore,
) : ModelCatalogRepository {

    override suspend fun fetchModels(): Outcome<List<AiModel>, DataError> = store.withApiKey(::fetchAllPages)

    /** Walks `offset` until a page comes back short or without a `next` link. */
    private suspend fun fetchAllPages(apiKey: ApiKey): Outcome<List<AiModel>, DataError> {
        val models = mutableListOf<AiModel>()
        var offset = 0
        while (true) {
            val page = when (val result = api.listModelsForKey(apiKey, offset, OpenRouterApi.MAX_PAGE_SIZE)) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return Outcome.Failure(result.error.toDataError())
            }
            page.data.mapNotNullTo(models) { it.toAiModel() }
            if (page.links?.next == null || page.data.size < OpenRouterApi.MAX_PAGE_SIZE) break
            offset += page.data.size
        }
        return Outcome.Success(models)
    }
}
