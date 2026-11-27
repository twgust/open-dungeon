package dev.opendungeon.domain.story

import dev.opendungeon.core.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Where stories live once they begin.
 *
 * Storage failures are expected, if rare, and come back as [StorageError]; nothing here throws them.
 */
public interface StoryRepository {
    /** The story with [id], or `null` when there is none. */
    public suspend fun story(id: StoryId): Outcome<Story?, StorageError>

    /** The story saved most recently, to continue from; `null` while there is none. Follows every save. */
    public val latest: Flow<StoryId?>

    /** Writes [story] and removes the draft it began from, in one go, so a begun story is never both. */
    public suspend fun begin(story: Story, from: DraftId): Outcome<Unit, StorageError>

    /** Writes [story]'s beats, guide and unsent text. Its setup is fixed once it begins and is not written again. */
    public suspend fun save(story: Story): Outcome<Unit, StorageError>
}
