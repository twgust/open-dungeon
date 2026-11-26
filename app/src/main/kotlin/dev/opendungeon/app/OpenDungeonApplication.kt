package dev.opendungeon.app

import android.app.Application
import dev.opendungeon.app.di.AppGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

class OpenDungeonApplication :
    Application(),
    MetroApplication {

    val graph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }

    override val appComponentProviders: MetroAppComponentProviders
        get() = graph
}
