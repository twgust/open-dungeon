package dev.opendungeon.tools.continuity.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import dev.opendungeon.tools.continuity.context.PolicySpec
import dev.opendungeon.tools.continuity.di.EnvironmentKey
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.model.ReasoningChoice
import dev.opendungeon.tools.continuity.run.RunEvent
import dev.opendungeon.tools.continuity.run.RunPlan
import kotlinx.serialization.SerializationException
import java.io.IOException

/** Judges an earlier run's answers again, so judges can be compared on the very same answers. */
internal class RejudgeCommand : SuspendingCliktCommand(name = "rejudge") {

    override fun help(context: Context): String =
        "Judge the answers of an earlier run again with --judge-model, without asking the model under test " +
            "anything. Writes a new report beside the old one, in rejudged-<model>-<time>/."

    private val scenario by argument(help = "The scenario the run was made with: a file, or a bundled name")
    private val run by argument(help = "The run's report directory, holding report.json and samples.json")
        .path(mustExist = true, canBeFile = false)
    private val judgeModel by option("--judge-model", help = "OpenRouter id of the judge to use this time")
        .required()
    private val concurrency by option("--concurrency", help = "Judge calls in flight at once")
        .int().restrictTo(min = 1).default(DEFAULT_CONCURRENCY)
    private val keyFile by option(
        "--key-file",
        help = "A file holding the OpenRouter key; else ${EnvironmentKey.VARIABLE} is read",
    ).path(mustExist = true, canBeDir = false)

    override suspend fun run() {
        val loaded = loadScenario(scenario)
        val (old, samples) = try {
            ReportFiles.readHeader(run) to ReportFiles.readSamples(run)
        } catch (e: IOException) {
            fail("Could not read the run in '$run': ${e.message}")
        } catch (e: SerializationException) {
            fail("'$run' does not hold a run's report.json and samples.json: ${e.message}")
        }
        if (old.scenario != loaded.id) fail("The run in '$run' was made with '${old.scenario}', not '${loaded.id}'")
        val policies = old.policies.map { p -> PolicySpec.parse(p.policy).getOrElse { fail(it.message.orEmpty()) } }
        val graph = harness(keyFile)
        val resolved = resolve(graph, old.subjectModel, judgeModel)
        val plan = RunPlan(
            scenario = loaded,
            policies = policies,
            // Only named in the report: nothing is asked of the model under test.
            subject = ModelChoice.subject(resolved.getValue(old.subjectModel), ReasoningChoice.Default, null),
            judge = ModelChoice.judge(resolved.getValue(judgeModel)),
            samples = old.samplesPerProbe,
            concurrency = concurrency,
        )
        echo("Judging ${samples.size} answers from ${old.createdAt} again with $judgeModel.")
        graph.rejudge.rejudge(plan, samples).collect { event ->
            if (event is RunEvent.Finished) {
                val directory = run.resolve("rejudged-${judgeModel.substringAfterLast('/')}-${timestamp()}")
                echo(ReportFiles.write(event.result, directory))
                echo("Report written to $directory")
            }
        }
    }

    private companion object {
        const val DEFAULT_CONCURRENCY = 6
    }
}
