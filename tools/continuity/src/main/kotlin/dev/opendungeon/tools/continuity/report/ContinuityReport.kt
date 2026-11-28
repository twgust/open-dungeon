package dev.opendungeon.tools.continuity.report

import dev.opendungeon.tools.continuity.run.ProbeSample
import dev.opendungeon.tools.continuity.run.RunResult
import dev.opendungeon.tools.continuity.run.SampleOutcome
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.opendungeon.tools.continuity.scenario.ProbeId
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/** The numbers a run produced, per policy, and every leak and contradiction behind them. */
@Serializable
internal data class ContinuityReport(
    val scenario: String,
    val title: String,
    val turns: Int,
    val subjectModel: String,
    val judgeModel: String,
    val samplesPerProbe: Int,
    val createdAt: String,
    val distanceBuckets: List<String>,
    val policies: List<PolicyReport>,
    val incidents: List<Incident>,
) {
    companion object {
        /** Upper bounds, in turns, of the distance buckets; the last bucket is open. Chosen around the windows in question. */
        val BucketBounds: List<Int> = listOf(3, 10, 30, 100, 300)

        fun of(result: RunResult, createdAt: String): ContinuityReport {
            val plan = result.plan
            val scoring = Scoring(plan.scenario)
            val byPolicy = result.samples.groupBy { it.policy }
            val incidents = result.samples.flatMap { sample ->
                scoring.checks(sample).filter { it.leaked || it.suspected || it.contradicted }.map { Incident.of(it) }
            }
            return ContinuityReport(
                scenario = plan.scenario.id,
                title = plan.scenario.title,
                turns = plan.scenario.turns.size,
                subjectModel = plan.subject.model.id.value,
                judgeModel = plan.judge.model.id.value,
                samplesPerProbe = plan.samples,
                createdAt = createdAt,
                distanceBuckets = bucketLabels(),
                policies = plan.policies.map { policy ->
                    PolicyReport.of(policy.name, byPolicy[policy.name].orEmpty(), scoring)
                },
                incidents = incidents,
            )
        }

        fun bucketOf(distance: Int): Int =
            BucketBounds.indexOfFirst { distance <= it }.takeIf { it >= 0 } ?: BucketBounds.size

        private fun bucketLabels(): List<String> {
            val lows = listOf(0) + BucketBounds.map { it + 1 }
            return lows.mapIndexed { index, low -> BucketBounds.getOrNull(index)?.let { "$low–$it" } ?: "$low+" }
        }
    }
}

/**
 * How one policy did.
 *
 * @property narratorRecall expected facts stated in answers to questions and actions.
 * @property recallByDistance the same, per distance bucket, in [ContinuityReport.distanceBuckets] order.
 * @property characterRecall expected facts stated by characters: what they should know and do. A low
 *   rate here with few leaks means the policy hides too much, which makes characters dull and amnesiac.
 * @property leaks forbidden facts stated, out of all forbidden checks.
 * @property suspicions forbidden facts raised only as a suspicion, out of all forbidden checks.
 * @property contradictions expected and guarded facts contradicted, out of all such checks.
 */
@Serializable
internal data class PolicyReport(
    val policy: String,
    val narratorRecall: Rate,
    val recallByDistance: List<Rate>,
    val characterRecall: Rate,
    val leaks: Rate,
    val suspicions: Rate,
    val contradictions: Rate,
    val probesPassed: Rate,
    val meanPromptTokens: Int?,
    val meanPromptCharacters: Int?,
    val costUsd: String?,
    val unanswered: Int,
    val unjudged: Int,
    val probes: List<ProbeScore>,
) {
    companion object {
        fun of(policy: String, samples: List<ProbeSample>, scoring: Scoring): PolicyReport {
            val checks = samples.flatMap(scoring::checks)
            val expected = checks.filter { it.role == Role.Expect }
            val narrator = expected.filterNot { it.byCharacter }
            val buckets = narrator.groupBy { ContinuityReport.bucketOf(it.distance) }
            val stable = checks.filter { it.role != Role.Forbid }
            val usages = samples.mapNotNull { it.outcome.usage() }
            val passes = samples.mapNotNull(scoring::passed)
            return PolicyReport(
                policy = policy,
                narratorRecall = narrator.rate { it.recalled },
                recallByDistance = (0..ContinuityReport.BucketBounds.size).map {
                    buckets[it].orEmpty().rate { c ->
                        c.recalled
                    }
                },
                characterRecall = expected.filter { it.byCharacter }.rate { it.recalled },
                leaks = checks.filter { it.role == Role.Forbid }.rate { it.leaked },
                suspicions = checks.filter { it.role == Role.Forbid }.rate { it.suspected },
                contradictions = stable.rate { it.contradicted },
                probesPassed = Rate.of(passes.count { it }, passes.size),
                meanPromptTokens = usages.takeIf { it.isNotEmpty() }?.map { it.promptTokens }?.average()?.toInt(),
                meanPromptCharacters = samples.takeIf {
                    it.isNotEmpty()
                }?.map { it.promptCharacters }?.average()?.toInt(),
                costUsd = usages.mapNotNull { it.costUsd?.toBigDecimal() }.takeIf { it.isNotEmpty() }
                    ?.fold(BigDecimal.ZERO, BigDecimal::add)?.toPlainString(),
                unanswered = samples.count { it.outcome is SampleOutcome.Unanswered },
                unjudged = samples.count { it.outcome is SampleOutcome.Unjudged },
                probes = samples.groupBy { it.probe }.map { (probe, runs) ->
                    val judged = runs.mapNotNull(scoring::passed)
                    ProbeScore(probe, Rate.of(judged.count { it }, judged.size))
                },
            )
        }

        private fun SampleOutcome.usage() = when (this) {
            is SampleOutcome.Judged -> usage
            is SampleOutcome.Unjudged -> usage
            is SampleOutcome.Unanswered -> null
        }

        private inline fun List<ScoredCheck>.rate(hit: (ScoredCheck) -> Boolean): Rate = Rate.of(count(hit), size)
    }
}

/** How often a probe's answers were wholly right under one policy. */
@Serializable
internal data class ProbeScore(val probe: ProbeId, val passed: Rate)

/** A leak, a suspicion of a forbidden fact, or a contradiction, with the words that gave it away. */
@Serializable
internal data class Incident(
    val policy: String,
    val probe: ProbeId,
    val sample: Int,
    val fact: FactId,
    val kind: Kind,
    val quote: String,
) {
    enum class Kind { Leak, Suspicion, Contradiction }

    companion object {
        fun of(check: ScoredCheck): Incident {
            require(check.leaked || check.suspected || check.contradicted) {
                "Check of ${check.fact} in ${check.probe} is no incident"
            }
            return Incident(
                policy = check.policy,
                probe = check.probe,
                sample = check.sample,
                fact = check.fact,
                kind = when {
                    check.leaked -> Kind.Leak
                    check.suspected -> Kind.Suspicion
                    else -> Kind.Contradiction
                },
                quote = check.quote,
            )
        }
    }
}
