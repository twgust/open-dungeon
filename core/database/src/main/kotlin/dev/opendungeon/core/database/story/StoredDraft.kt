package dev.opendungeon.core.database.story

import androidx.room.Embedded
import androidx.room.Relation

/** A draft row read together with its characters. */
public data class StoredDraft(
    @Embedded val draft: StoryDraftEntity,
    @Relation(parentColumn = "id", entityColumn = "draft_id")
    val characters: List<DraftCharacterEntity>,
)
