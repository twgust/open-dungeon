package dev.opendungeon.core.database.play

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A person of a begun story: the protagonist or one of the cast, told apart by [slot]. Written once,
 * when the story begins, and removed with it. Keyed within its story, so two stories never contend for
 * one person's id.
 */
@Entity(
    tableName = "story_character",
    primaryKeys = ["story_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("story_id")],
)
public data class StoryCharacterEntity(
    val id: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    val slot: String,
    /** Order within the slot. */
    val position: Int,
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
