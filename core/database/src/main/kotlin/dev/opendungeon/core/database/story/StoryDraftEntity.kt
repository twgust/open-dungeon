package dev.opendungeon.core.database.story

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One draft's scalar choices. Characters and takes live in their own tables. */
@Entity(tableName = "story_draft")
public data class StoryDraftEntity(
    @PrimaryKey val id: String,
    val step: String,
    val genres: List<PhraseColumns>,
    @Embedded(prefix = "twist_") val twist: PhraseColumns?,
    @Embedded(prefix = "tone_") val tone: PhraseColumns?,
    @Embedded(prefix = "setting_") val setting: PhraseColumns?,
    @Embedded(prefix = "premise_") val premise: PhraseColumns?,
    @ColumnInfo(name = "limit_desire") val limitDesire: String,
    @ColumnInfo(name = "limit_violence") val limitViolence: String,
    @ColumnInfo(name = "limit_note") val limitNote: String?,
    @ColumnInfo(name = "first_line") val firstLine: String,
    @ColumnInfo(name = "chosen_take_id") val chosenTakeId: String?,
)

/** A choice in the story's sentence: its words, and whether the player wrote them. */
@Serializable
public data class PhraseColumns(
    val text: String,
    val own: Boolean,
)
