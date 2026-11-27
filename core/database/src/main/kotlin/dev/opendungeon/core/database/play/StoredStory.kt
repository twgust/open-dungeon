package dev.opendungeon.core.database.play

import androidx.room.Embedded
import androidx.room.Relation

/** A story row read together with its people and its beats. */
public data class StoredStory(
    @Embedded val story: StoryEntity,
    @Relation(parentColumn = "id", entityColumn = "story_id")
    val characters: List<StoryCharacterEntity>,
    @Relation(parentColumn = "id", entityColumn = "story_id")
    val beats: List<BeatEntity>,
)
