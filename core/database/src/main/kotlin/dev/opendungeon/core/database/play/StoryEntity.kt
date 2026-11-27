package dev.opendungeon.core.database.play

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.opendungeon.core.database.story.PhraseColumns

/**
 * A begun story's choices, fixed once written, and what the player is writing on it. Its people and its
 * beats live in their own tables.
 */
@Entity(tableName = "story", indices = [Index("updated_at")])
public data class StoryEntity(
    @PrimaryKey val id: String,
    /** `null` until the story is named. Written only by renaming or naming, never by a save. */
    val title: String?,
    val genres: List<PhraseColumns>,
    @Embedded(prefix = "twist_") val twist: PhraseColumns?,
    @Embedded(prefix = "tone_") val tone: PhraseColumns?,
    @Embedded(prefix = "setting_") val setting: PhraseColumns?,
    @Embedded(prefix = "premise_") val premise: PhraseColumns?,
    @ColumnInfo(name = "limit_desire") val limitDesire: String,
    @ColumnInfo(name = "limit_violence") val limitViolence: String,
    @ColumnInfo(name = "limit_note") val limitNote: String?,
    val guide: String?,
    @ColumnInfo(name = "unsent_turn") val unsentTurn: String,
    /** The open composer's kind, or `null` when none is open; its text and beat go with it. */
    @ColumnInfo(name = "composer_kind") val composerKind: String?,
    @ColumnInfo(name = "composer_text") val composerText: String?,
    @ColumnInfo(name = "composer_beat_id") val composerBeatId: String?,
    /** When the story was last saved, in epoch milliseconds: the library lists the latest first. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * What a save writes of a story's row: everything that changes in play, and nothing else. Its choices are
 * fixed once begun and its title is written apart, so a save can never undo a rename made meanwhile.
 */
public data class StoryProgress(
    val id: String,
    val guide: String?,
    @ColumnInfo(name = "unsent_turn") val unsentTurn: String,
    @ColumnInfo(name = "composer_kind") val composerKind: String?,
    @ColumnInfo(name = "composer_text") val composerText: String?,
    @ColumnInfo(name = "composer_beat_id") val composerBeatId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A story as the library lists it, read without its people or beats. */
public data class StoryListing(
    val id: String,
    val title: String?,
    val genres: List<PhraseColumns>,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
