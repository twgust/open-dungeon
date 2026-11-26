package dev.opendungeon.data.openrouter

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.OpenRouterApi
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Pages through `GET /models/user` with the stored key. */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultModelCatalogRepository(private val api: OpenRouterApi, private val store: ApiKeyStore) :
    ModelCatalogRepository {

    override suspend fun fetchModels(): Outcome<List<AiModel>, DataError> = store.withApiKey(::fetchAllPages)

    /**
     * Walks `offset` until a page comes back short or without a `next` link. A catalog that keeps going
     * past [MAX_PAGES] full pages is a misbehaving `next` link, not a real one, and fails rather than loops.
     */
    private suspend fun fetchAllPages(apiKey: ApiKey): Outcome<List<AiModel>, DataError> {
        val models = mutableListOf<AiModel>()
        var offset = 0
        repeat(MAX_PAGES) {
            val page = when (val result = api.listModelsForKey(apiKey, offset, OpenRouterApi.MAX_PAGE_SIZE)) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return Outcome.Failure(result.error.toDataError())
            }
            page.data.mapTo(models) { it.toAiModel() }
            if (page.links?.next == null || page.data.size < OpenRouterApi.MAX_PAGE_SIZE) return Outcome.Success(models)
            offset += page.data.size
        }
        return Outcome.Failure(DataError.UnexpectedResponse)
    }

    private companion object {
        /** 50,000 models, about a hundred times today's catalog. */
        const val MAX_PAGES = 50
    }
}
