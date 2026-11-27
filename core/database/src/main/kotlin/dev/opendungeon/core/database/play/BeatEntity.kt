package dev.opendungeon.core.database.play

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One beat of a story, at [position] from the first. Removed with its story. */
@Entity(
    tableName = "story_beat",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["story_id", "position"])],
)
public data class BeatEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    val position: Int,
    val kind: String,
    val text: String,
)
