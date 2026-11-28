package dev.opendungeon.tools.continuity.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import dev.opendungeon.tools.continuity.context.PolicySpec
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.run.RunEvent
import dev.opendungeon.tools.continuity.run.RunPlan

/** Replays a scenario through each policy, probes it, and writes the report. */
internal class RunCommand : SuspendingCliktCommand(name = "run") {

    override fun help(context: Context): String =
        "Replay a scenario through each --policy, ask every probe --samples times, judge the answers and " +
            "write report.md, report.json and samples.json. Policies: full, recent:N, chars:N, each optionally " +
            "after perceived/. Default: ${PolicySpec.Defaults.joinToString()}."

    private val scenario by argument(help = "A scenario file, or the name of a bundled scenario")
    private val policies by option("--policy", help = "A context policy to compare; repeat for several").multiple()
    private val samples by option(
        "--samples",
        help = "Answers per probe and policy",
    ).int().restrictTo(min = 1).default(DEFAULT_SAMPLES)
    private val temperature by option(
        "--temperature",
        help = "Temperature for the model under test; the provider's own when left out",
    )
        .double().restrictTo(0.0..2.0)
    private val out by option("--out", help = "Directory for the report").path(canBeFile = false)
    private val models by ModelOptions()

    override suspend fun run() {
        val loaded = loadScenario(scenario)
        val chosen = (policies.ifEmpty { PolicySpec.Defaults }).map { spec ->
            PolicySpec.parse(spec).getOrElse { fail(it.message.orEmpty()) }
        }
        if (chosen.map { it.name }.distinct().size != chosen.size) fail("A policy is named twice")
        val graph = harness(models)
        val resolved = resolve(graph, models.model, models.judgeModelId)
        val plan = RunPlan(
            scenario = loaded,
            policies = chosen,
            subject = ModelChoice.subject(resolved.getValue(models.model), models.reasoning, temperature),
            judge = ModelChoice.judge(resolved.getValue(models.judgeModelId)),
            samples = samples,
            concurrency = models.concurrency,
        )
        echo(
            "${loaded.probes.size} probes × ${chosen.size} policies × $samples sample(s) on ${models.model}, judged by ${models.judgeModelId}.",
        )
        var reported = 0
        graph.run.run(plan).collect { event ->
            when (event) {
                is RunEvent.SampleFinished -> if (event.finished * PROGRESS_STEPS / event.total > reported) {
                    reported = event.finished * PROGRESS_STEPS / event.total
                    echo("Answers: ${event.finished}/${event.total}")
                }
                is RunEvent.Finished -> {
                    val directory = out ?: OutputRoot.resolve("${loaded.id}-${timestamp()}")
                    echo(ReportFiles.write(event.result, directory))
                    echo("Report written to $directory")
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_SAMPLES = 3

        /** Progress is printed at every tenth of the way. */
        const val PROGRESS_STEPS = 10
    }
}
