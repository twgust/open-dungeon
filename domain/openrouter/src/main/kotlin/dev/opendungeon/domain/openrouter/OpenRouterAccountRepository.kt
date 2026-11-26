package dev.opendungeon.domain.openrouter

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.Outcome
import kotlinx.coroutines.flow.Flow

/** The player's link to OpenRouter: whether a key is stored, and connecting or disconnecting one. */
public interface OpenRouterAccountRepository {
    public val isConnected: Flow<Boolean>

    /** Checks [rawKey] against OpenRouter and stores it only if OpenRouter accepts it. */
    public suspend fun connect(rawKey: String): Outcome<KeyInfo, DataError>

    /** Details of the stored key, fetched fresh. */
    public suspend fun currentKey(): Outcome<KeyInfo, DataError>

    /** Removes the stored key. Fails only when the device cannot remove it. */
    public suspend fun disconnect(): Outcome<Unit, DataError>
}
