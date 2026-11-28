package dev.opendungeon.tools.continuity.report

import dev.opendungeon.tools.continuity.context.Reader
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.probe.ProbePrompts
import dev.opendungeon.tools.continuity.run.ProbeSample
import dev.opendungeon.tools.continuity.run.SampleOutcome
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.opendungeon.tools.continuity.scenario.Probe
import dev.opendungeon.tools.continuity.scenario.ProbeId
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.scenario.Timeline

/** What a fact was checked for in a probe. */
internal enum class Role { Expect, Forbid, Guard }

/** One fact in one judged answer, with everything a report groups by. */
internal data class ScoredCheck(
    val policy: String,
    val probe: ProbeId,
    val sample: Int,
    val fact: FactId,
    val role: Role,
    /** Whether a character answered for themselves, rather than the narrator for the story. */
    val byCharacter: Boolean,
    /** Turns from the fact's turn to the probe's; negative for a fact the story has not reached yet. */
    val distance: Int,
    val verdict: Verdict,
    val quote: String,
) {
    /** An expected fact the answer stated. */
    val recalled: Boolean get() = role == Role.Expect && verdict == Verdict.Asserted

    /** A forbidden fact the answer stated: someone knew what they should not. */
    val leaked: Boolean get() = role == Role.Forbid && verdict == Verdict.Asserted

    /**
     * A forbidden fact the answer raised only as a suspicion. Not a leak by itself: a character may guess.
     * Comparing the rate with the `none` policy's, whose reader was told nothing, says how much is a guess.
     */
    val suspected: Boolean get() = role == Role.Forbid && verdict == Verdict.Suspected

    /** An answer that broke the story. A forbidden fact being denied is not a contradiction, only discretion. */
    val contradicted: Boolean get() = role != Role.Forbid && verdict == Verdict.Contradicted
}

/** Turns judged answers into checks. Pure, so every number in a report can be traced to its verdicts. */
internal class Scoring(private val scenario: Scenario) {

    private val timeline = Timeline(scenario)
    private val probes: Map<ProbeId, Probe> = scenario.probes.associateBy { it.id }

    /** The checks in [sample]; none when it was never answered or judged. */
    fun checks(sample: ProbeSample): List<ScoredCheck> {
        val judged = sample.outcome as? SampleOutcome.Judged ?: return emptyList()
        val probe = probes.getValue(sample.probe)
        val byCharacter = ProbePrompts.readerOf(probe) is Reader.Character
        return judged.verdicts.map { verdict ->
            val fact = requireNotNull(scenario.fact(verdict.fact)) { "No fact ${verdict.fact} in ${scenario.id}" }
            ScoredCheck(
                policy = sample.policy,
                probe = probe.id,
                sample = sample.sample,
                fact = fact.id,
                role = probe.roleOf(fact.id),
                byCharacter = byCharacter,
                distance = timeline.distance(fact, probe),
                verdict = verdict.verdict,
                quote = verdict.quote,
            )
        }
    }

    /** Whether [sample] got everything right: every expected fact stated, nothing leaked, nothing contradicted. */
    fun passed(sample: ProbeSample): Boolean? {
        if (sample.outcome !is SampleOutcome.Judged) return null
        val probe = probes.getValue(sample.probe)
        val checks = checks(sample)
        return checks.count { it.recalled } == probe.expect.size && checks.none { it.leaked || it.contradicted }
    }

    private fun Probe.roleOf(fact: FactId): Role = when (fact) {
        in expect -> Role.Expect
        in forbid -> Role.Forbid
        in guard -> Role.Guard
        else -> error("Probe $id does not check $fact")
    }
}
