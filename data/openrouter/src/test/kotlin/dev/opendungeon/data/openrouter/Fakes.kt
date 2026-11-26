package dev.opendungeon.data.openrouter

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.network.NetworkResult
import dev.opendungeon.core.network.OpenRouterApi
import dev.opendungeon.core.network.dto.CurrentKeyDto
import dev.opendungeon.core.network.dto.ModelsPageDto
import kotlinx.coroutines.flow.MutableStateFlow

internal val VALID_KEY_TEXT = "sk-or-v1-" + "cd".repeat(32)
internal val VALID_KEY = requireNotNull(ApiKey.parse(VALID_KEY_TEXT))

internal class FakeApiKeyStore(initial: ApiKey? = null) : ApiKeyStore {
    override val apiKey = MutableStateFlow(initial)
    override suspend fun write(apiKey: ApiKey) { this.apiKey.value = apiKey }
    override suspend fun clear() { apiKey.value = null }
}

internal class FakeOpenRouterApi(
    private val pages: (offset: Int) -> NetworkResult<ModelsPageDto> = { error("unexpected listModelsForKey") },
    private val key: () -> NetworkResult<CurrentKeyDto> = { error("unexpected getCurrentKey") },
) : OpenRouterApi {
    val requestedOffsets = mutableListOf<Int>()

    override suspend fun listModelsForKey(apiKey: ApiKey, offset: Int, limit: Int): NetworkResult<ModelsPageDto> {
        requestedOffsets += offset
        return pages(offset)
    }

    override suspend fun getCurrentKey(apiKey: ApiKey): NetworkResult<CurrentKeyDto> = key()
}
