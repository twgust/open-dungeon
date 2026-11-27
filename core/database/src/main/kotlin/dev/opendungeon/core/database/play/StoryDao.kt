package dev.opendungeon.core.database.play

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // One query per thing done to a story; Room needs each as its own function.
public interface StoryDao {

    /** A story in the trash is not read. */
    @Transaction
    @Query("SELECT * FROM story WHERE id = :id AND deleted_at IS NULL")
    public suspend fun get(id: String): StoredStory?

    /** Stories in the trash are left out. Ties on the time are broken by id, so the order never flickers. */
    @Query(
        "SELECT id, title, genres, updated_at FROM story WHERE deleted_at IS NULL ORDER BY updated_at DESC, id",
    )
    public fun listings(): Flow<List<StoryListing>>

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
    public suspend fun update(story: StoryProgress, changed: List<BeatEntity>, removed: List<String>) {
        updateProgress(story)
        // One by one rather than `IN (…)`: older SQLite caps a statement at 999 arguments.
        removed.forEach { deleteBeat(it) }
        upsertBeats(changed)
    }

    /** Writes a story's row and all of its beats, replacing whatever beats it had, in one transaction. */
    @Transaction
    public suspend fun replace(story: StoryProgress, beats: List<BeatEntity>) {
        updateProgress(story)
        deleteBeats(story.id)
        upsertBeats(beats)
    }

    /** An upsert rather than a replace: a replace deletes the row first, and the cascade takes its children. */
    @Upsert
    public suspend fun upsertStory(story: StoryEntity)

    /**
     * Writes what changes in play, and only when the story is still stored: a story removed meanwhile is
     * not brought back, and its beats then fail their foreign key rather than be written without it.
     */
    @Update(entity = StoryEntity::class)
    public suspend fun updateProgress(progress: StoryProgress)

    @Query("UPDATE story SET title = :title WHERE id = :id")
    public suspend fun rename(id: String, title: String)

    @Query("UPDATE story SET title = :title WHERE id = :id AND title IS NULL")
    public suspend fun nameIfUntitled(id: String, title: String)

    @Query("UPDATE story SET deleted_at = :at WHERE id = :id")
    public suspend fun trash(id: String, at: Long)

    @Query("UPDATE story SET deleted_at = NULL WHERE id = :id")
    public suspend fun restore(id: String)

    /** Their people and beats go with them, by the cascade. */
    @Query("DELETE FROM story WHERE deleted_at IS NOT NULL")
    public suspend fun emptyTrash()

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
