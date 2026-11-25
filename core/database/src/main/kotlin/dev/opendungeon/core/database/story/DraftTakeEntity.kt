package dev.opendungeon.core.database.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One audition of a draft's opening turn. Removed with its draft. */
@Entity(
    tableName = "draft_take",
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
public data class DraftTakeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "draft_id") val draftId: String,
    val position: Int,
    @ColumnInfo(name = "model_id") val modelId: String,
    val text: String,
    val state: String,
)
