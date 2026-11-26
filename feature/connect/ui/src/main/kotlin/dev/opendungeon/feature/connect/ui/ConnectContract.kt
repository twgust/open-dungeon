package dev.opendungeon.feature.connect.ui

import dev.opendungeon.core.model.DataError

/*
 * The screen renders the interactor's Connection as it is; there is no UI state of its own. The key being
 * typed is deliberately not part of any state: it stays in the text field until submitted, so the secret
 * is never held by the ViewModel or captured in a state dump.
 */

sealed interface ConnectIntent {
    data class Submit(val rawKey: String) : ConnectIntent {
        override fun toString(): String = "Submit(rawKey=██)"
    }

    data object Refresh : ConnectIntent

    data object Disconnect : ConnectIntent
}

sealed interface ConnectEffect {
    /** Something the player did could not be kept; the screen already shows it taken back. */
    data class ShowFailure(val error: DataError) : ConnectEffect
}
