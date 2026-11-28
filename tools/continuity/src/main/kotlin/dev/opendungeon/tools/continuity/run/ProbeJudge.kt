package dev.opendungeon.tools.continuity.run

import dev.opendungeon.tools.continuity.judge.Judge
import dev.opendungeon.tools.continuity.judge.JudgeFailure
import dev.opendungeon.tools.continuity.judge.JudgeResult
import dev.opendungeon.tools.continuity.judge.WhoIsWho
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.probe.ProbePrompts
import dev.opendungeon.tools.continuity.scenario.Probe
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Judges one answer to one probe against every fact the probe checks. A run judges each answer as it
 * arrives; a rejudge judges the answers of an earlier run again, with another judge or a better prompt.
 */
internal interface ProbeJudge {
    suspend fun judge(scenario: Scenario, probe: Probe, answer: ProbeAnswer, model: ModelChoice): SampleOutcome
}

/** What the model under test wrote, and what it cost. */
internal data class ProbeAnswer(val text: String, val cutOff: Boolean, val usage: SampleUsage?)

@Inject
@ContributesBinding(AppScope::class)
internal class DefaultProbeJudge(private val judge: Judge) : ProbeJudge {

    override suspend fun judge(
        scenario: Scenario,
        probe: Probe,
        answer: ProbeAnswer,
        model: ModelChoice,
    ): SampleOutcome {
        if (answer.text.isEmpty()) return SampleOutcome.Unanswered("The model answered with nothing")
        val facts = probe.facts.map { requireNotNull(scenario.fact(it)) { "No fact $it in ${scenario.id}" } }
        val prompt = ProbePrompts.ask(scenario, probe)
        return when (val result = judge.judge(answer.text, facts, WhoIsWho.of(scenario), prompt, model)) {
            is JudgeResult.Judged -> SampleOutcome.Judged(
                answer = answer.text,
                cutOff = answer.cutOff,
                usage = answer.usage,
                verdicts = facts.map { fact ->
                    val verdict = result.verdicts.getValue(fact.id)
                    JudgedFact(fact.id, verdict.verdict, verdict.quote)
                },
            )
            is JudgeResult.Failed -> SampleOutcome.Unjudged(answer.text, answer.usage, result.reason.describe())
        }
    }

    private fun JudgeFailure.describe(): String = when (this) {
        is JudgeFailure.Generation -> "The judge failed: $error"
        is JudgeFailure.Incomplete -> "The judge's verdicts were incomplete: $detail"
    }
}
