package dev.opendungeon.feature.connect

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import kotlinx.collections.immutable.ImmutableList

/** Screen state for linking the player's OpenRouter key and browsing the models it unlocks. */
/**
 * The key being typed is deliberately not part of this state: it stays in the text field until
 * submitted, so the secret is never held by the ViewModel or captured in a state dump.
 */
data class ConnectState(
    val phase: Phase = Phase.Loading,
) {
    sealed interface Phase {
        /** Reading whether a key is already stored. */
        data object Loading : Phase

        data class Disconnected(val error: DataError? = null) : Phase

        data object Validating : Phase

        data class Connected(val key: Remote<KeyInfo>, val models: Remote<ImmutableList<AiModel>>) : Phase
    }

    /** A value fetched from OpenRouter. */
    sealed interface Remote<out T> {
        data object Loading : Remote<Nothing>

        data class Loaded<T>(val value: T) : Remote<T>

        data class Failed(val error: DataError) : Remote<Nothing>
    }
}

sealed interface ConnectIntent {
    data class Submit(val rawKey: String) : ConnectIntent {
        override fun toString(): String = "Submit(rawKey=██)"
    }

    data object Refresh : ConnectIntent

    data object Disconnect : ConnectIntent
}
