package dev.opendungeon.data.openrouter

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.OpenRouterApi
import dev.opendungeon.domain.openrouter.OpenRouterAccountRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Validates keys with `GET /key` and keeps accepted ones in the [ApiKeyStore]. */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultOpenRouterAccountRepository(
    private val api: OpenRouterApi,
    private val store: ApiKeyStore,
) : OpenRouterAccountRepository {

    override val isConnected: Flow<Boolean> = store.apiKey.map { it != null }.distinctUntilChanged()

    override suspend fun connect(rawKey: String): Outcome<KeyInfo, DataError> {
        val apiKey = ApiKey.parse(rawKey) ?: return Outcome.Failure(DataError.MalformedApiKey)
        return fetchKeyInfo(apiKey).also { if (it is Outcome.Success) store.write(apiKey) }
    }

    override suspend fun currentKey(): Outcome<KeyInfo, DataError> = store.withApiKey(::fetchKeyInfo)

    override suspend fun disconnect() {
        store.clear()
    }

    private suspend fun fetchKeyInfo(apiKey: ApiKey): Outcome<KeyInfo, DataError> =
        api.getCurrentKey(apiKey).toDataOutcome { it.toKeyInfo() }
}
