package dev.opendungeon.core.database.play

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface StoryDao {

    @Transaction
    @Query("SELECT * FROM story WHERE id = :id")
    public suspend fun get(id: String): StoredStory?

    @Query("SELECT id FROM story ORDER BY updated_at DESC LIMIT 1")
    public fun latest(): Flow<String?>

    /**
     * Writes a new story whole and removes the draft it began from, in one transaction, so a reader never
     * sees the story half written, or both the story and its draft.
     */
    @Transaction
    public suspend fun begin(stored: StoredStory, draftId: String) {
        upsertStory(stored.story)
        insertCharacters(stored.characters)
        upsertBeats(stored.beats)
        deleteDraft(draftId)
    }

    /**
     * Writes a story's row and only the beats that changed, in one transaction, leaving its people as they
     * are: [changed] are upserted and [removed] deleted by id, so a long story is never written whole again.
     */
    @Transaction
    public suspend fun update(story: StoryEntity, changed: List<BeatEntity>, removed: List<String>) {
        upsertStory(story)
        // One by one rather than `IN (…)`: older SQLite caps a statement at 999 arguments.
        removed.forEach { deleteBeat(it) }
        upsertBeats(changed)
    }

    /** Writes a story's row and all of its beats, replacing whatever beats it had, in one transaction. */
    @Transaction
    public suspend fun replace(story: StoryEntity, beats: List<BeatEntity>) {
        upsertStory(story)
        deleteBeats(story.id)
        upsertBeats(beats)
    }

    /** An upsert rather than a replace: a replace deletes the row first, and the cascade takes its children. */
    @Upsert
    public suspend fun upsertStory(story: StoryEntity)

    @Insert
    public suspend fun insertCharacters(characters: List<StoryCharacterEntity>)

    @Upsert
    public suspend fun upsertBeats(beats: List<BeatEntity>)

    @Query("DELETE FROM story_beat WHERE id = :id")
    public suspend fun deleteBeat(id: String)

    @Query("DELETE FROM story_beat WHERE story_id = :storyId")
    public suspend fun deleteBeats(storyId: String)

    @Query("DELETE FROM story_draft WHERE id = :draftId")
    public suspend fun deleteDraft(draftId: String)
}
