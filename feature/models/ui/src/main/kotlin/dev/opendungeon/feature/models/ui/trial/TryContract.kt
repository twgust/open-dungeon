package dev.opendungeon.feature.models.ui.trial

// The screen renders the run's TryState as it is; the prompt being typed stays in its text field.

sealed interface TryIntent {
    data class Send(val prompt: String) : TryIntent

    data object Stop : TryIntent

    data object Leave : TryIntent

    data object ConfirmLeave : TryIntent

    data object Stay : TryIntent
}

sealed interface TryEffect {
    data object Leave : TryEffect
}
