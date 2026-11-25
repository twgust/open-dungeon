package dev.opendungeon.domain.story

import kotlinx.coroutines.flow.Flow

/** Where drafts live. There is at most one open draft at a time. */
public interface StoryDraftRepository {
    /** The open draft, if creation was started and neither finished nor discarded. */
    public suspend fun openDraft(): StoryDraft?

    public fun observe(id: DraftId): Flow<StoryDraft?>

    /** Writes [draft] whole. Writing a draft with a new id replaces any other open draft. */
    public suspend fun save(draft: StoryDraft)

    public suspend fun discard(id: DraftId)
}
