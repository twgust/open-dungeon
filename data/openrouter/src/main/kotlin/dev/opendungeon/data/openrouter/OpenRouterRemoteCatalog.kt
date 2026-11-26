package dev.opendungeon.data.openrouter

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.OpenRouterApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Pages through `GET /models/user` and reads `GET /models/{author}/{slug}/endpoints` with the stored key. */
@Inject
@ContributesBinding(AppScope::class)
public class OpenRouterRemoteCatalog(private val api: OpenRouterApi, private val store: ApiKeyStore) :
    RemoteModelCatalog {

    override suspend fun fetchModels(sort: ModelSort?): Outcome<List<AiModel>, DataError> =
        store.withApiKey { fetchAllPages(it, sort) }

    override suspend fun fetchEndpoints(id: ModelId): Outcome<List<ModelEndpoint>, DataError> =
        store.withApiKey { apiKey ->
            api.listEndpoints(apiKey, id.value).toDataOutcome { dto -> dto.endpoints.map { it.toModelEndpoint() } }
        }

    /**
     * Walks `offset` until a page comes back short or without a `next` link. A catalog that keeps going
     * past [MAX_PAGES] full pages is a misbehaving `next` link, not a real one, and fails rather than loops.
     * Models that cannot answer a chat request are left out; see [servesChat].
     */
    private suspend fun fetchAllPages(apiKey: ApiKey, sort: ModelSort?): Outcome<List<AiModel>, DataError> {
        val models = mutableListOf<AiModel>()
        var offset = 0
        repeat(MAX_PAGES) {
            val result = api.listModelsForKey(apiKey, offset, OpenRouterApi.MAX_PAGE_SIZE, sort?.wireName)
            val page = when (result) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return Outcome.Failure(result.error.toDataError())
            }
            page.data.filter { it.servesChat }.mapTo(models) { it.toAiModel() }
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
