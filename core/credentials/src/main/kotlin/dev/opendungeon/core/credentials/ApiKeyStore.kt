package dev.opendungeon.core.credentials

import dev.opendungeon.core.model.ApiKey
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/** Persists the player's OpenRouter key on this device. Implementations must encrypt it at rest. */
public interface ApiKeyStore {
    /** The stored key, or `null` when none is stored or it cannot be read. Emits again whenever it changes. */
    public val apiKey: Flow<ApiKey?>

    /** @throws IOException when the device cannot store the key. */
    public suspend fun write(apiKey: ApiKey)

    /** @throws IOException when the device cannot remove the key. */
    public suspend fun clear()
}
