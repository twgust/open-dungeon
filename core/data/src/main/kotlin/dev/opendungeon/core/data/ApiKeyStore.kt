package dev.opendungeon.core.data

import dev.opendungeon.core.model.ApiKey
import kotlinx.coroutines.flow.Flow

/** Persists the player's OpenRouter key on this device. Implementations must encrypt it at rest. */
public interface ApiKeyStore {
    /** The stored key, or `null` when none is stored. Emits again whenever it changes. */
    public val apiKey: Flow<ApiKey?>

    public suspend fun write(apiKey: ApiKey)

    public suspend fun clear()
}
