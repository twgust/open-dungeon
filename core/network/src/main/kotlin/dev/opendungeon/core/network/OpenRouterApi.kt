package dev.opendungeon.core.network

import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.network.dto.CurrentKeyDto
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
     */
    public suspend fun listModelsForKey(apiKey: ApiKey, offset: Int, limit: Int): NetworkResult<ModelsPageDto>

    /** `GET /key`: label, usage and limits of [apiKey]. A 401 means the key is not valid. */
    public suspend fun getCurrentKey(apiKey: ApiKey): NetworkResult<CurrentKeyDto>

    public companion object {
        public const val BASE_URL: String = "https://openrouter.ai/api/v1/"
        public const val MAX_PAGE_SIZE: Int = 1000
    }
}
