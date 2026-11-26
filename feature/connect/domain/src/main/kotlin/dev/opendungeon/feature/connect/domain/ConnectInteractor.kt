package dev.opendungeon.feature.connect.domain

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Linking the player's OpenRouter key and loading what it unlocks. Callers say what the player did; the
 * interactor decides whether it is allowed and what follows. Every verb shows its effect at once.
 */
public interface ConnectInteractor {

    public val state: StateFlow<Connection>

    /** Failures that are not part of [state], because the state has already gone back to how it was. */
    public val failures: Flow<DataError>

    /** Checks [rawKey] with OpenRouter and keeps it if accepted. Ignored while a key is being checked. */
    public fun connect(rawKey: String)

    /** Fetches the key's details and the model catalog again. Ignored unless connected. */
    public fun refresh()

    /**
     * Forgets the key and the catalog it fetched. If the device cannot remove the key, the connection comes
     * back and [failures] says so.
     */
    public fun disconnect()

    /** Makes an interactor whose work lives as long as [scope], typically a screen's. */
    public fun interface Factory {
        public fun create(scope: CoroutineScope): ConnectInteractor
    }
}

/** Where the player's link to OpenRouter stands. */
public sealed interface Connection {
    /** Reading whether a key is stored. */
    public data object Loading : Connection

    /** No key. [error] is why the last key was not accepted, if one was tried. */
    public data class Disconnected(val error: DataError? = null) : Connection

    public data object Validating : Connection

    public data class Connected(val key: Remote<KeyInfo>, val model: ModelChoice) : Connection
}

/** The model the player writes with, as far as the stored catalog tells. */
public sealed interface ModelChoice {
    /** No catalog is stored yet and one is being fetched. */
    public data object Loading : ModelChoice

    /** No catalog is stored and fetching one failed. */
    public data class Unavailable(val error: DataError) : ModelChoice

    /** Nothing is chosen, or the chosen model is no longer offered. */
    public data object None : ModelChoice

    public data class Chosen(val model: AiModel) : ModelChoice
}

/** A value fetched from OpenRouter. */
public sealed interface Remote<out T> {
    public data object Loading : Remote<Nothing>

    public data class Loaded<T>(val value: T) : Remote<T>

    public data class Failed(val error: DataError) : Remote<Nothing>
}
