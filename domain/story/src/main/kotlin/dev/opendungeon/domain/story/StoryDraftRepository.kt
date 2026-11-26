package dev.opendungeon.domain.story

import dev.opendungeon.core.model.Outcome

/**
 * Where drafts live. There is at most one open draft at a time.
 *
 * Storage failures are expected, if rare, and come back as [StorageError]; nothing here throws them.
 */
public interface StoryDraftRepository {
    /** The open draft, or `null` when creation was never started or was finished or discarded. */
    public suspend fun openDraft(): Outcome<StoryDraft?, StorageError>

    /** The draft with [id], or `null` when there is none. */
    public suspend fun draft(id: DraftId): Outcome<StoryDraft?, StorageError>

    /** Writes [draft] whole. Writing a draft with a new id replaces any other open draft. */
    public suspend fun save(draft: StoryDraft): Outcome<Unit, StorageError>

    public suspend fun discard(id: DraftId): Outcome<Unit, StorageError>
}
