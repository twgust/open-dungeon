package dev.opendungeon.core.network

import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.network.dto.CurrentKeyDto
import dev.opendungeon.core.network.dto.ModelEndpointsDto
import dev.opendungeon.core.network.dto.ModelsPageDto

/**
 * The slice of the OpenRouter REST API (v1) the app uses outside of inference.
 *
 * The key is passed per call rather than read from ambient state: the same client validates a
 * candidate key before it is stored and serves the stored key afterwards.
 */
public interface OpenRouterApi {

    /**
     * `GET /models/user`: the text-output models this key may use, after the player's provider
     * preferences, privacy settings and guardrails are applied. Paginated by [offset] and [limit] (max 1000).
     *
     * @param sort one of the documented `sort` values, ranking the list server side; `null` keeps
     *   OpenRouter's default order.
     */
    public suspend fun listModelsForKey(
        apiKey: ApiKey,
        offset: Int,
        limit: Int,
        sort: String?,
    ): NetworkResult<ModelsPageDto>

    /**
     * `GET /models/{author}/{slug}/endpoints`: every provider serving [modelId], each with its own
     * parameters, limits and prices. [modelId] is a catalog id such as `deepseek/deepseek-chat-v3.1`.
     */
    public suspend fun listEndpoints(apiKey: ApiKey, modelId: String): NetworkResult<ModelEndpointsDto>

    /** `GET /key`: label, usage and limits of [apiKey]. A 401 means the key is not valid. */
    public suspend fun getCurrentKey(apiKey: ApiKey): NetworkResult<CurrentKeyDto>

    public companion object {
        public const val MAX_PAGE_SIZE: Int = 1000
    }
}
