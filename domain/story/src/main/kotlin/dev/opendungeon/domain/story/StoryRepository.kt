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
    /** The story with [id], or `null` when there is none or it is in the trash. */
    public suspend fun story(id: StoryId): Outcome<Story?, StorageError>

    /**
     * Every story but those in the trash, the one played last first. Follows every change. A database that cannot be read offers
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

    /**
     * Puts the story in the trash: it is no longer listed or read, and stays stored until [emptyTrash], so
     * [restore] can bring it back exactly as it was, even after the app restarts. Writes run in the order
     * they are called, so a restore right after a trash always wins.
     */
    public suspend fun trash(id: StoryId): Outcome<Unit, StorageError>

    /** Takes the story out of the trash. A story that is not in it is left as it is. */
    public suspend fun restore(id: StoryId): Outcome<Unit, StorageError>

    /** Removes every story in the trash for good, with everything in them. */
    public suspend fun emptyTrash(): Outcome<Unit, StorageError>
}
