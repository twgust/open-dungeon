package dev.opendungeon.feature.models.ui.picker

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort

// The screen renders the picker's PickerState as it is; there is no UI state of its own.

sealed interface PickerIntent {
    data class Search(val query: String) : PickerIntent

    data class SortBy(val sort: ModelSort?) : PickerIntent

    data class ToggleInput(val modality: String) : PickerIntent

    data object ToggleFreeOnly : PickerIntent

    data object ToggleTools : PickerIntent

    data class ToggleContext(val tokens: Int) : PickerIntent

    data object Refresh : PickerIntent

    data class Choose(val id: ModelId) : PickerIntent
}

sealed interface PickerEffect {
    data object OpenChosen : PickerEffect

    /** Something the player did could not be kept; the screen already shows it taken back. */
    data class ShowFailure(val error: DataError) : PickerEffect
}
