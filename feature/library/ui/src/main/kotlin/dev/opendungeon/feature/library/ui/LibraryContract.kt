package dev.opendungeon.feature.library.ui

import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.library.domain.LibraryFailure
import dev.opendungeon.feature.library.domain.LibraryState

/** What the library renders: the library's own state, already decided. */
data class LibraryUiState(val library: LibraryState)

/** Everything the player does in the library, said as it happened. */
sealed interface LibraryIntent {
    data class Open(val story: StoryId) : LibraryIntent

    data object NewStory : LibraryIntent

    /** The pen on a story. */
    data class Edit(val story: StoryId) : LibraryIntent

    data class TypeTitle(val text: String) : LibraryIntent

    data object SaveTitle : LibraryIntent

    data object CancelEdit : LibraryIntent

    data class Delete(val story: StoryId) : LibraryIntent

    data object Undo : LibraryIntent
}

sealed interface LibraryEffect {
    data class OpenStory(val story: StoryId) : LibraryEffect

    data object StartStory : LibraryEffect

    /** Something the player did could not be kept; the screen already shows it taken back. */
    data class ShowFailure(val failure: LibraryFailure) : LibraryEffect
}
