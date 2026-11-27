package dev.opendungeon.tools.continuity.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.model.ReasoningChoice
import dev.opendungeon.tools.continuity.pad.PadEvent
import dev.opendungeon.tools.continuity.pad.PadRequest
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.scenario.ScenarioCheck
import dev.opendungeon.tools.continuity.scenario.ScenarioCodec
import java.nio.file.Files
import java.nio.file.Path

/** Stretches a hand-written scenario to a long story with model-written interludes. */
internal class PadCommand : SuspendingCliktCommand(name = "pad") {

    override fun help(context: Context): String =
        "Stretch a scenario to --turns turns by writing interludes between its turns. Facts and probes stay on " +
            "the hand-written turns, so distances grow while every fact stays exact."

    private val scenario by argument(help = "A scenario file, or the name of a bundled scenario")
    private val turns by option("--turns", help = "How many turns the padded story has").int().required()
    private val out by option("--out", help = "Where to write the padded scenario").path(canBeDir = false)
    private val models by ModelOptions()

    override suspend fun run() {
        val loaded = loadScenario(scenario)
        if (turns < loaded.turns.size) fail("${loaded.id} already has ${loaded.turns.size} turns")
        val graph = harness(models)
        val resolved = resolve(graph, models.model, models.judgeModelId)
        val request = PadRequest(
            scenario = loaded,
            targetTurns = turns,
            // Interludes are filler: the writer need not think, and should not.
            writer = ModelChoice.subject(resolved.getValue(models.model), ReasoningChoice.Least, temperature = null),
            judge = ModelChoice.judge(resolved.getValue(models.judgeModelId)),
            concurrency = models.concurrency,
        )
        val target = out ?: OutputRoot.resolve("${loaded.id}-$turns.json")
        var reported = 0
        graph.pad.pad(request).collect { event ->
            when (event) {
                is PadEvent.InterludeAccepted -> if (event.written * PROGRESS_STEPS / event.total > reported) {
                    reported = event.written * PROGRESS_STEPS / event.total
                    echo("Interludes: ${event.written}/${event.total}")
                }
                is PadEvent.Failed -> fail(
                    event.reasons.joinToString(
                        prefix = "No acceptable interlude could be written after '${event.after}':\n- ",
                        separator = "\n- ",
                    ),
                )
                is PadEvent.Finished -> write(event.scenario, target)
            }
        }
    }

    private fun write(padded: Scenario, target: Path) {
        val problems = ScenarioCheck.problems(padded)
        check(problems.isEmpty()) { "Padding broke the scenario: $problems" }
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.writeString(target, ScenarioCodec.encode(padded))
        echo("Wrote ${padded.turns.size} turns to $target. Read some interludes before trusting it, then run it.")
    }

    private companion object {
        /** Progress is printed at every tenth of the way. */
        const val PROGRESS_STEPS = 10
    }
}
