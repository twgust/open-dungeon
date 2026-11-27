package dev.opendungeon.core.database.di

import android.content.Context
import androidx.room.Room
import dev.opendungeon.core.database.OpenDungeonDatabase
import dev.opendungeon.core.database.model.CatalogDao
import dev.opendungeon.core.database.model.RankingDao
import dev.opendungeon.core.database.model.SetupDao
import dev.opendungeon.core.database.play.StoryDao
import dev.opendungeon.core.database.story.StoryDraftDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers

@BindingContainer
@ContributesTo(AppScope::class)
public object DatabaseBindings {

    @Provides
    @SingleIn(AppScope::class)
    public fun provideDatabase(context: Context): OpenDungeonDatabase =
        Room.databaseBuilder(context, OpenDungeonDatabase::class.java, OpenDungeonDatabase.FILE_NAME)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides
    public fun provideStoryDraftDao(database: OpenDungeonDatabase): StoryDraftDao = database.storyDraftDao()

    @Provides
    public fun provideCatalogDao(database: OpenDungeonDatabase): CatalogDao = database.catalogDao()

    @Provides
    public fun provideRankingDao(database: OpenDungeonDatabase): RankingDao = database.rankingDao()

    @Provides
    public fun provideSetupDao(database: OpenDungeonDatabase): SetupDao = database.setupDao()

    @Provides
    public fun provideStoryDao(database: OpenDungeonDatabase): StoryDao = database.storyDao()
}
