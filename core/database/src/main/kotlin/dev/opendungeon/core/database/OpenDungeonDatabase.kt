package dev.opendungeon.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.opendungeon.core.database.story.DraftCharacterEntity
import dev.opendungeon.core.database.story.DraftTakeEntity
import dev.opendungeon.core.database.story.StoryDraftDao
import dev.opendungeon.core.database.story.StoryDraftEntity

/**
 * The app's one on-device database. Tables mirror storage needs, not domain types: the data modules
 * map between the two, so the domain can change shape without a migration and vice versa.
 *
 * Schemas are exported to `core/database/schemas` and checked in, so every version bump has a
 * reviewable diff and a migration to test against.
 */
@Database(
    entities = [StoryDraftEntity::class, DraftCharacterEntity::class, DraftTakeEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(StorageConverters::class)
public abstract class OpenDungeonDatabase : RoomDatabase() {
    public abstract fun storyDraftDao(): StoryDraftDao

    public companion object {
        public const val FILE_NAME: String = "open-dungeon.db"
    }
}
