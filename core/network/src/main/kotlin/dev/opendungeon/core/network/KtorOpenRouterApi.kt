package dev.opendungeon.core.network

import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.map
import dev.opendungeon.core.network.di.OpenRouterHttpClient
import dev.opendungeon.core.network.dto.CurrentKeyDto
import dev.opendungeon.core.network.dto.CurrentKeyResponseDto
import dev.opendungeon.core.network.dto.ModelsPageDto
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.http.HttpMethod
import io.ktor.http.path

@Inject
@ContributesBinding(AppScope::class)
public class KtorOpenRouterApi(
    @OpenRouterHttpClient baseClient: HttpClient,
) : OpenRouterApi {

    private val client = baseClient.forRest()

    override suspend fun listModelsForKey(apiKey: ApiKey, offset: Int, limit: Int): NetworkResult<ModelsPageDto> {
        require(offset >= 0) { "offset must be >= 0, was $offset" }
        require(limit in 1..OpenRouterApi.MAX_PAGE_SIZE) {
            "limit must be in 1..${OpenRouterApi.MAX_PAGE_SIZE}, was $limit"
        }
        return client.safeRequest {
            method = HttpMethod.Get
            url { path("models", "user") }
            bearerAuth(apiKey.value)
            parameter("offset", offset)
            parameter("limit", limit)
        }
    }

    override suspend fun getCurrentKey(apiKey: ApiKey): NetworkResult<CurrentKeyDto> =
        client.safeRequest<CurrentKeyResponseDto> {
            method = HttpMethod.Get
            url { path("key") }
            bearerAuth(apiKey.value)
        }.map(CurrentKeyResponseDto::data)
}
