package dev.opendungeon.data.openrouter

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.flatMap
import dev.opendungeon.core.model.map
import dev.opendungeon.core.network.OpenRouterApi
import dev.opendungeon.domain.openrouter.OpenRouterAccountRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Validates keys with `GET /key` and keeps accepted ones in the [ApiKeyStore]. */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultOpenRouterAccountRepository(private val api: OpenRouterApi, private val store: ApiKeyStore) :
    OpenRouterAccountRepository {

    override val isConnected: Flow<Boolean> = store.apiKey.map { it != null }.distinctUntilChanged()

    override suspend fun connect(rawKey: String): Outcome<KeyInfo, DataError> {
        val apiKey = ApiKey.parse(rawKey) ?: return Outcome.Failure(DataError.MalformedApiKey)
        return fetchKeyInfo(apiKey).flatMap { info -> keyStorage { store.write(apiKey) }.map { info } }
    }

    override suspend fun currentKey(): Outcome<KeyInfo, DataError> = store.withApiKey(::fetchKeyInfo)

    override suspend fun disconnect(): Outcome<Unit, DataError> = keyStorage { store.clear() }

    private inline fun keyStorage(block: () -> Unit): Outcome<Unit, DataError> = try {
        Outcome.Success(block())
    } catch (_: IOException) {
        Outcome.Failure(DataError.KeyStorage)
    }

    private suspend fun fetchKeyInfo(apiKey: ApiKey): Outcome<KeyInfo, DataError> =
        api.getCurrentKey(apiKey).toDataOutcome { it.toKeyInfo() }
}
