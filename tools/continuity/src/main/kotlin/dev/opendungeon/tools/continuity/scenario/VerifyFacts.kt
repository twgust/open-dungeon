package dev.opendungeon.tools.continuity.scenario

import dev.opendungeon.tools.continuity.judge.Judge
import dev.opendungeon.tools.continuity.judge.JudgeFailure
import dev.opendungeon.tools.continuity.judge.JudgeResult
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.judge.WhoIsWho
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Asks the judge whether each fact really is stated by the turn that is said to establish it. A fact its
 * own turn does not state would turn every probe about it into a test of the scenario, not the policy.
 */
internal interface VerifyFacts {
    /** Every fact the judge did not find in its turn, as a sentence each; empty when all were found. */
    suspend fun problems(scenario: Scenario, judge: ModelChoice, concurrency: Int): List<String>
}

@Inject
@ContributesBinding(AppScope::class)
internal class DefaultVerifyFacts(private val judge: Judge) : VerifyFacts {

    override suspend fun problems(scenario: Scenario, judge: ModelChoice, concurrency: Int): List<String> {
        val permits = Semaphore(concurrency)
        val turns = scenario.turns.associateBy { it.id }
        return coroutineScope {
            scenario.facts.groupBy { it.establishedIn }.map { (turnId, facts) ->
                async { permits.withPermit { problemsIn(scenario, turns.getValue(turnId), facts, judge) } }
            }.awaitAll().flatten()
        }
    }

    private suspend fun problemsIn(
        scenario: Scenario,
        turn: ScriptedTurn,
        facts: List<Fact>,
        model: ModelChoice,
    ): List<String> {
        val passage = listOfNotNull(turn.player, turn.narrationText).joinToString(separator = "\n\n")
        return when (val result = judge.judge(passage, facts, WhoIsWho.of(scenario), model)) {
            is JudgeResult.Failed -> listOf(
                when (val reason = result.reason) {
                    is JudgeFailure.Generation -> "Turn '${turn.id}' could not be judged: ${reason.error}"
                    is JudgeFailure.Incomplete -> "Turn '${turn.id}' could not be judged: ${reason.detail}"
                },
            )
            is JudgeResult.Judged -> facts.mapNotNull { fact ->
                val verdict = result.verdicts.getValue(fact.id)
                "Fact '${fact.id}' is ${verdict.verdict.name.lowercase()} in its turn '${turn.id}'"
                    .takeIf { verdict.verdict != Verdict.Asserted }
            }
        }
    }
}
