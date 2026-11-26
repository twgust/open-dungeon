package dev.opendungeon.core.database.story

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
public interface StoryDraftDao {

    @Transaction
    @Query("SELECT * FROM story_draft LIMIT 1")
    public suspend fun first(): StoredDraft?

    @Transaction
    @Query("SELECT * FROM story_draft WHERE id = :id")
    public suspend fun get(id: String): StoredDraft?

    /**
     * Writes [stored] whole and removes every other draft, in one transaction, so readers never see a
     * draft with half its characters or two open drafts.
     */
    @Transaction
    public suspend fun replaceAll(stored: StoredDraft) {
        val id = stored.draft.id
        deleteAllExcept(id)
        upsert(stored.draft)
        deleteCharacters(id)
        insertCharacters(stored.characters)
    }

    @Query("DELETE FROM story_draft WHERE id = :id")
    public suspend fun delete(id: String)

    @Query("DELETE FROM story_draft WHERE id != :id")
    public suspend fun deleteAllExcept(id: String)

    /** An upsert rather than a replace: a replace deletes the row first, and the cascade takes its children. */
    @Upsert
    public suspend fun upsert(draft: StoryDraftEntity)

    @Query("DELETE FROM draft_character WHERE draft_id = :draftId")
    public suspend fun deleteCharacters(draftId: String)

    @Insert
    public suspend fun insertCharacters(characters: List<DraftCharacterEntity>)
}
