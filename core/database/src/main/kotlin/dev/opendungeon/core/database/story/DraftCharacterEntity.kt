package dev.opendungeon.core.database.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A character of a draft: the protagonist or a cast member, told apart by [slot].
 * Removed with its draft.
 */
@Entity(
    tableName = "draft_character",
    foreignKeys = [
        ForeignKey(
            entity = StoryDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draft_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("draft_id")],
)
public data class DraftCharacterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "draft_id") val draftId: String,
    val slot: String,
    /** Order within the slot. */
    val position: Int,
    /** Cast only: whether the app offered them or the player wrote them. */
    val origin: String?,
    @ColumnInfo(name = "in_story") val inStory: Boolean,
    val role: String?,
    val name: String?,
    val age: String?,
    val gender: String?,
    val appearance: String?,
    val personality: List<String>,
    val speech: String?,
    @ColumnInfo(name = "voice_line") val voiceLine: String?,
    val wants: String?,
    val flaw: String?,
    val habit: String?,
    @ColumnInfo(name = "good_at") val goodAt: String?,
    @ColumnInfo(name = "secret_kind") val secretKind: String,
    @ColumnInfo(name = "secret_text") val secretText: String?,
)
