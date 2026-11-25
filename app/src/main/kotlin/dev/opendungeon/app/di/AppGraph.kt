package dev.opendungeon.app.di

import android.app.Application
import android.content.Context
import dev.opendungeon.core.network.AppAttribution
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The application-wide graph. Modules contribute to [AppScope] with `@ContributesBinding`,
 * `@ContributesIntoMap` and `@ContributesTo`; nothing is registered here by hand.
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph, MetroAppComponentProviders {

    @Provides
    fun provideContext(application: Application): Context = application

    @Provides
    fun provideAppAttribution(): AppAttribution =
        AppAttribution(title = "Open Dungeon", siteUrl = "https://github.com/twgust/open-dungeon")

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
