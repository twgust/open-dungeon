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
    /** When the story was last saved, in epoch milliseconds: the story to continue is the latest. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
