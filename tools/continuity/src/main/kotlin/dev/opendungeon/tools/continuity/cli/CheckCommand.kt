package dev.opendungeon.tools.continuity.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.opendungeon.tools.continuity.model.ModelChoice

/** Checks a scenario's structure, and with `--verify` has the judge confirm each fact is in its turn. */
internal class CheckCommand : SuspendingCliktCommand(name = "check") {

    override fun help(context: Context): String =
        "Check a scenario's structure. With --verify, the judge also confirms each fact is stated in its turn."

    private val scenario by argument(help = "A scenario file, or the name of a bundled scenario")
    private val verify by option(
        "--verify",
        help = "Have the judge confirm every fact (costs a few cents at most)",
    ).flag()
    private val models by ModelOptions()

    override suspend fun run() {
        val loaded = loadScenario(scenario)
        echo(
            "${loaded.id}: ${loaded.turns.size} turns, ${loaded.facts.size} facts, ${loaded.probes.size} probes. Structure is sound.",
        )
        if (!verify) return
        val graph = harness(models)
        val judge = resolve(graph, models.judgeModelId).getValue(models.judgeModelId)
        val problems = graph.verify.problems(loaded, ModelChoice.judge(judge), models.concurrency)
        if (problems.isNotEmpty()) {
            fail(
                problems.joinToString(prefix = "The judge did not find every fact:\n- ", separator = "\n- "),
            )
        }
        echo("The judge found every fact in its turn.")
    }
}
