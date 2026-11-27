package dev.opendungeon.feature.library.domain

import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The player's stories: listing them, naming them and putting them away. Callers say what the player did;
 * the library decides whether it is allowed and what follows. Verbs that mean nothing in the current state
 * are ignored, so a double tap or a tap that lands late changes nothing.
 *
 * Every verb shows its effect at once. If a change cannot be kept, the state goes back to how it was and
 * [failures] says why. Untitled stories are named by the drafting model in the background, one at a time,
 * once per library; a story it cannot name stays untitled until the next.
 */
public interface Library {

    public val state: StateFlow<LibraryState>

    /** Changes that could not be kept. The state already shows the stories as they were before. */
    public val failures: Flow<LibraryFailure>

    /** Opens [story]'s title for editing. */
    public fun edit(story: StoryId)

    /** The title being edited, as typed. Cut to [TitleEdit.maxLength]. */
    public fun typeTitle(text: String)

    /** Keeps the title being edited, when [TitleEdit.canSave]. */
    public fun saveTitle()

    /** Closes the title being edited, keeping nothing of it. */
    public fun cancelEdit()

    /**
     * Deletes the story being edited. It leaves the list at once and can be taken back with [undoDelete]
     * for a few seconds; only then is it removed for good. A second delete removes the first for good.
     */
    public fun delete()

    /** Takes back the last delete, while [LibraryState.undo] offers it. */
    public fun undoDelete()

    /** Makes a library whose work lives as long as [scope], typically a screen's. */
    public fun interface Factory {
        public fun create(scope: CoroutineScope): Library
    }
}

/** Everything the library shows, already decided. Screens only render it. */
public data class LibraryState(
    /** Until the stories are first read; nothing is listed meanwhile. */
    val loading: Boolean = true,
    /** The one played last first, as they now stand: a rename shows at once, a deleted story is gone. */
    val stories: List<StorySummary> = emptyList(),
    val editing: TitleEdit? = null,
    /** The story just deleted, while the delete can be taken back. */
    val undo: StoryId? = null,
)

/** A story's title open for editing, with [text] as typed so far. */
public data class TitleEdit(
    val story: StoryId,
    val text: String,
    val maxLength: Int,
    /** There is a title to keep, and it is not the one the story already has. */
    val canSave: Boolean,
)

/** A change the player made that could not be kept. */
public sealed interface LibraryFailure {
    public val error: StorageError

    public data class Rename(override val error: StorageError) : LibraryFailure

    /** The story is back in the list, as it was. */
    public data class Delete(override val error: StorageError) : LibraryFailure
}
