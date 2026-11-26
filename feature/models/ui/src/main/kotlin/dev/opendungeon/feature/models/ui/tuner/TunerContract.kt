package dev.opendungeon.feature.models.ui.tuner

import dev.opendungeon.core.model.DataError
import dev.opendungeon.feature.models.domain.TuningEdit

// The screen renders the tuner's TunerState as it is; there is no UI state of its own.

sealed interface TunerIntent {
    data class Edit(val edit: TuningEdit) : TunerIntent

    data object ResetAll : TunerIntent

    data object RetryProviders : TunerIntent
}

sealed interface TunerEffect {
    /** An edit could not be kept; the screen already shows it taken back. */
    data class ShowFailure(val error: DataError) : TunerEffect
}
