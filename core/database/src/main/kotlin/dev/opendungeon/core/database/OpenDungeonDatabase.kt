package dev.opendungeon.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.opendungeon.core.database.model.CatalogDao
import dev.opendungeon.core.database.model.CatalogFetchEntity
import dev.opendungeon.core.database.model.CatalogModelEntity
import dev.opendungeon.core.database.model.CatalogRankEntity
import dev.opendungeon.core.database.model.ChosenModelEntity
import dev.opendungeon.core.database.model.ModelTuningEntity
import dev.opendungeon.core.database.model.RankingDao
import dev.opendungeon.core.database.model.SetupDao
import dev.opendungeon.core.database.play.BeatEntity
import dev.opendungeon.core.database.play.StoryCharacterEntity
import dev.opendungeon.core.database.play.StoryDao
import dev.opendungeon.core.database.play.StoryEntity
import dev.opendungeon.core.database.story.DraftCharacterEntity
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
    entities = [
        StoryDraftEntity::class,
        DraftCharacterEntity::class,
        CatalogModelEntity::class,
        CatalogFetchEntity::class,
        CatalogRankEntity::class,
        ChosenModelEntity::class,
        ModelTuningEntity::class,
        StoryEntity::class,
        StoryCharacterEntity::class,
        BeatEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // 2 adds the model catalog, the chosen model and tunings: new tables only.
        AutoMigration(from = 1, to = 2),
        // 3 replaces the auditioned takes with one opening: see [OpeningMigration].
        AutoMigration(from = 2, to = 3, spec = OpeningMigration::class),
        // 4 adds begun stories, their people and their beats: new tables only.
        AutoMigration(from = 3, to = 4),
        // 5 adds story titles: one nullable column, so every story arrives untitled.
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(StorageConverters::class)
public abstract class OpenDungeonDatabase : RoomDatabase() {
    public abstract fun storyDraftDao(): StoryDraftDao

    public abstract fun catalogDao(): CatalogDao

    public abstract fun rankingDao(): RankingDao

    public abstract fun setupDao(): SetupDao

    public abstract fun storyDao(): StoryDao

    public companion object {
        public const val FILE_NAME: String = "open-dungeon.db"
    }
}
