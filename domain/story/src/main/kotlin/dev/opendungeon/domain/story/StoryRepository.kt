package dev.opendungeon.domain.story

import dev.opendungeon.core.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Where stories live once they begin.
 *
 * A story's title is written apart from its play: [save] never writes it, and only [rename] and
 * [nameIfUntitled] do, so a screen holding an older copy of a story can never undo a name given meanwhile.
 *
 * Storage failures are expected, if rare, and come back as [StorageError]; nothing here throws them.
 */
public interface StoryRepository {
    /** The story with [id], or `null` when there is none. */
    public suspend fun story(id: StoryId): Outcome<Story?, StorageError>

    /**
     * Every story, the one played last first. Follows every change. A database that cannot be read offers
     * no stories, rather than fail whoever follows it.
     */
    public val stories: Flow<List<StorySummary>>

    /** Writes [story] and removes the draft it began from, in one go, so a begun story is never both. */
    public suspend fun begin(story: Story, from: DraftId): Outcome<Unit, StorageError>

    /**
     * Writes [story]'s beats, guide and unsent text. Its setup is fixed once it begins, and its title is
     * written apart, so neither is written again. A story that is no longer stored is not brought back.
     */
    public suspend fun save(story: Story): Outcome<Unit, StorageError>

    /** Calls the story [title], whatever it was called. Does not change when it was last played. */
    public suspend fun rename(id: StoryId, title: String): Outcome<Unit, StorageError>

    /** Calls the story [title] only if it has no title yet, so a name the player gave is never replaced. */
    public suspend fun nameIfUntitled(id: StoryId, title: String): Outcome<Unit, StorageError>

    /** Removes the story with everything in it. Removing a story that is not there succeeds. */
    public suspend fun remove(id: StoryId): Outcome<Unit, StorageError>
}
