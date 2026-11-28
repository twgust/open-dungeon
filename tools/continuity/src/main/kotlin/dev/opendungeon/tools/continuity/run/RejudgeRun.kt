package dev.opendungeon.tools.continuity.run

import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Judges the answers of an earlier run again, without asking the model under test anything. This is how a
 * judge is checked: the same answers, read by another judge or a revised prompt, and compared by a person
 * reading them. It costs only the judge's calls.
 */
internal interface RejudgeRun {
    /** A cold flow that reports each sample as it is judged again and ends with [RunEvent.Finished]. */
    fun rejudge(plan: RunPlan, samples: List<ProbeSample>): Flow<RunEvent>
}

@Inject
@ContributesBinding(AppScope::class)
internal class DefaultRejudgeRun(private val judge: ProbeJudge) : RejudgeRun {

    override fun rejudge(plan: RunPlan, samples: List<ProbeSample>): Flow<RunEvent> = channelFlow {
        val finished = AtomicInteger()
        val permits = Semaphore(plan.concurrency)
        val rejudged = samples.map { sample ->
            async {
                val again = permits.withPermit { again(plan.scenario, sample, plan.judge) }
                send(RunEvent.SampleFinished(again, finished.incrementAndGet(), samples.size))
                again
            }
        }.awaitAll()
        send(RunEvent.Finished(RunResult(plan, rejudged)))
    }

    /** [sample] judged by [model]; a sample the model never answered stays as it was. */
    private suspend fun again(scenario: Scenario, sample: ProbeSample, model: ModelChoice): ProbeSample {
        val answer = when (val outcome = sample.outcome) {
            is SampleOutcome.Judged -> ProbeAnswer(outcome.answer, outcome.cutOff, outcome.usage)
            // A sample the old judge could not read never recorded whether it was cut off.
            is SampleOutcome.Unjudged -> ProbeAnswer(outcome.answer, cutOff = false, usage = outcome.usage)
            is SampleOutcome.Unanswered -> return sample
        }
        val probe = requireNotNull(scenario.probes.firstOrNull { it.id == sample.probe }) {
            "Probe ${sample.probe} is not in ${scenario.id}; was this run made with another scenario?"
        }
        return sample.copy(outcome = judge.judge(scenario, probe, answer, model))
    }
}
