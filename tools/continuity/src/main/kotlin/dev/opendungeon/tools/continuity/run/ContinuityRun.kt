package dev.opendungeon.tools.continuity.run

import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.tools.continuity.context.ContextPolicy
import dev.opendungeon.tools.continuity.judge.Judge
import dev.opendungeon.tools.continuity.judge.JudgeFailure
import dev.opendungeon.tools.continuity.judge.JudgeResult
import dev.opendungeon.tools.continuity.probe.Completion
import dev.opendungeon.tools.continuity.probe.ProbePrompts
import dev.opendungeon.tools.continuity.probe.complete
import dev.opendungeon.tools.continuity.scenario.Probe
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

/** Replays a scenario through each policy and asks every probe, judging each answer. */
internal interface ContinuityRun {
    /** A cold flow that reports each sample as it finishes and ends with [RunEvent.Finished]. */
    fun run(plan: RunPlan): Flow<RunEvent>
}

@Inject
@ContributesBinding(AppScope::class)
internal class DefaultContinuityRun(private val generator: TextGenerator, private val judge: Judge) : ContinuityRun {

    override fun run(plan: RunPlan): Flow<RunEvent> = channelFlow {
        val prepared = plan.policies.flatMap { prepare(it, plan.scenario) }
        val total = prepared.size * plan.samples
        val finished = AtomicInteger()
        val permits = Semaphore(plan.concurrency)
        val samples = prepared.flatMap { probe ->
            (1..plan.samples).map { index ->
                async {
                    val sample = permits.withPermit { ask(plan, probe, index) }
                    send(RunEvent.SampleFinished(sample, finished.incrementAndGet(), total))
                    sample
                }
            }
        }.awaitAll()
        send(RunEvent.Finished(RunResult(plan, samples)))
    }

    /**
     * Plays the whole story through a fresh session of [policy], taking each probe's prompt right after its
     * turn. Prompts are fixed before any model is called, so the session never runs concurrently.
     */
    private suspend fun prepare(policy: ContextPolicy, scenario: Scenario): List<PreparedProbe> {
        val session = policy.open(scenario)
        val probesAfter = scenario.probes.groupBy { it.after }
        val prepared = mutableListOf<PreparedProbe>()
        for (turn in scenario.turns) {
            session.advance(turn)
            probesAfter[turn.id].orEmpty().forEach { probe ->
                val context = session.contextFor(ProbePrompts.readerOf(probe))
                prepared += PreparedProbe(policy.name, probe, ProbePrompts.messages(scenario, probe, context))
            }
        }
        // Back in the scenario's order, so reports list probes the way the scenario does.
        return prepared.sortedBy { scenario.probes.indexOf(it.probe) }
    }

    private suspend fun ask(plan: RunPlan, prepared: PreparedProbe, index: Int): ProbeSample {
        val request =
            TextRequest(model = plan.subject.model, messages = prepared.messages, tuning = plan.subject.tuning)
        val outcome = when (val completion = generator.complete(request)) {
            is Completion.Failed -> SampleOutcome.Unanswered(completion.error.toString())
            is Completion.Answered -> judged(plan, prepared.probe, completion)
        }
        return ProbeSample(
            policy = prepared.policy,
            probe = prepared.probe.id,
            sample = index,
            promptCharacters = prepared.messages.sumOf { it.text.length },
            outcome = outcome,
        )
    }

    private suspend fun judged(plan: RunPlan, probe: Probe, answer: Completion.Answered): SampleOutcome {
        val usage = answer.usage?.let(SampleUsage::of)
        if (answer.text.isEmpty()) return SampleOutcome.Unanswered("The model answered with nothing")
        val facts = probe.facts.map { requireNotNull(plan.scenario.fact(it)) { "No fact $it in ${plan.scenario.id}" } }
        return when (val result = judge.judge(answer.text, facts, plan.scenario.protagonist.name, plan.judge)) {
            is JudgeResult.Judged -> SampleOutcome.Judged(
                answer = answer.text,
                cutOff = answer.cutOff,
                usage = usage,
                verdicts = facts.map { fact ->
                    val verdict = result.verdicts.getValue(fact.id)
                    JudgedFact(fact.id, verdict.verdict, verdict.quote)
                },
            )
            is JudgeResult.Failed -> SampleOutcome.Unjudged(answer.text, usage, result.reason.describe())
        }
    }

    private fun JudgeFailure.describe(): String = when (this) {
        is JudgeFailure.Generation -> "The judge failed: $error"
        is JudgeFailure.Incomplete -> "The judge's verdicts were incomplete: $detail"
    }

    private data class PreparedProbe(val policy: String, val probe: Probe, val messages: List<ChatMessage>)
}
