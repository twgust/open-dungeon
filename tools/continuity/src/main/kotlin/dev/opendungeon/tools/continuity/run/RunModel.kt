package dev.opendungeon.tools.continuity.run

import dev.opendungeon.domain.generation.Usage
import dev.opendungeon.tools.continuity.context.ContextPolicy
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.opendungeon.tools.continuity.scenario.ProbeId
import dev.opendungeon.tools.continuity.scenario.Scenario
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One comparison: every probe of [scenario], through every policy, [samples] times each.
 *
 * @property concurrency how many model calls may be in flight at once.
 */
internal data class RunPlan(
    val scenario: Scenario,
    val policies: List<ContextPolicy>,
    val subject: ModelChoice,
    val judge: ModelChoice,
    val samples: Int,
    val concurrency: Int,
) {
    init {
        require(policies.isNotEmpty()) { "Compare at least one policy" }
        require(policies.map { it.name }.distinct().size == policies.size) { "Each policy runs once" }
        require(samples >= 1) { "Take at least one sample, not $samples" }
        require(concurrency >= 1) { "Allow at least one call at a time, not $concurrency" }
    }
}

internal sealed interface RunEvent {
    data class SampleFinished(val sample: ProbeSample, val finished: Int, val total: Int) : RunEvent

    data class Finished(val result: RunResult) : RunEvent
}

/** Everything a run produced, in plan order: policy, then probe, then sample. */
internal data class RunResult(val plan: RunPlan, val samples: List<ProbeSample>)

/** One answer to one probe through one policy, and how the judge read it. */
@Serializable
internal data class ProbeSample(
    val policy: String,
    val probe: ProbeId,
    val sample: Int,
    /** The length of the prompt the model got, a stand-in for tokens when the provider reports no usage. */
    val promptCharacters: Int,
    val outcome: SampleOutcome,
)

@Serializable
internal sealed interface SampleOutcome {
    @Serializable
    @SerialName("judged")
    data class Judged(
        val answer: String,
        val cutOff: Boolean,
        val usage: SampleUsage?,
        val verdicts: List<JudgedFact>,
    ) : SampleOutcome

    /** The model under test gave no answer. */
    @Serializable
    @SerialName("unanswered")
    data class Unanswered(val error: String) : SampleOutcome

    /** The model answered, but the judge could not read the answer. */
    @Serializable
    @SerialName("unjudged")
    data class Unjudged(val answer: String, val usage: SampleUsage?, val error: String) : SampleOutcome
}

@Serializable
internal data class JudgedFact(val fact: FactId, val verdict: Verdict, val quote: String)

/** [Usage] in a form that serialises; the cost stays a decimal string so no precision is lost. */
@Serializable
internal data class SampleUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val reasoningTokens: Int? = null,
    val costUsd: String? = null,
) {
    companion object {
        fun of(usage: Usage): SampleUsage = SampleUsage(
            promptTokens = usage.promptTokens,
            completionTokens = usage.completionTokens,
            reasoningTokens = usage.reasoningTokens,
            costUsd = usage.cost?.toPlainString(),
        )
    }
}
