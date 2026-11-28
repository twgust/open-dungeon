package dev.opendungeon.tools.continuity.di

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.network.AppAttribution
import dev.opendungeon.tools.continuity.model.ModelResolver
import dev.opendungeon.tools.continuity.pad.PadScenario
import dev.opendungeon.tools.continuity.run.ContinuityRun
import dev.opendungeon.tools.continuity.run.RejudgeRun
import dev.opendungeon.tools.continuity.scenario.VerifyFacts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

/**
 * The harness's object graph: the app's own network and generation bindings, contributed by the modules it
 * depends on, plus the harness's use cases. Only the key and the attribution come from outside, because
 * the app gets those from Android.
 */
@DependencyGraph(AppScope::class)
internal interface HarnessGraph {
    val run: ContinuityRun
    val rejudge: RejudgeRun
    val pad: PadScenario
    val verify: VerifyFacts
    val models: ModelResolver

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides keys: ApiKeyStore,
            @Provides attribution: AppAttribution,
        ): HarnessGraph
    }

    companion object {
        /** Shown on OpenRouter's activity log, so harness spend is easy to tell apart from play. */
        val Attribution: AppAttribution =
            AppAttribution(
                title = "Open Dungeon continuity harness",
                siteUrl = "https://github.com/twgust/open-dungeon",
            )
    }
}
