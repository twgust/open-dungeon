package dev.opendungeon.tools.continuity.run

import dev.opendungeon.core.model.DataError
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.tools.continuity.FakeJudge
import dev.opendungeon.tools.continuity.FakeTextGenerator
import dev.opendungeon.tools.continuity.JUDGE
import dev.opendungeon.tools.continuity.POISON
import dev.opendungeon.tools.continuity.SUBJECT
import dev.opendungeon.tools.continuity.completed
import dev.opendungeon.tools.continuity.context.PolicySpec
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.scenario.ProbeId
import dev.opendungeon.tools.continuity.tinyScenario
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContinuityRunTest {

    private fun plan(vararg policies: String, samples: Int = 2, concurrency: Int = 4) = RunPlan(
        scenario = tinyScenario(),
        policies = policies.map { PolicySpec.parse(it).getOrThrow() },
        subject = SUBJECT,
        judge = JUDGE,
        samples = samples,
        concurrency = concurrency,
    )

    @Test
    fun `every probe is asked through every policy, samples times, in plan order`() = runTest {
        val run = DefaultContinuityRun(FakeTextGenerator(), FakeJudge())

        val result = run.run(plan("full", "recent:1")).filterIsInstance<RunEvent.Finished>().single().result

        assertEquals(2 * 3 * 2, result.samples.size)
        assertEquals(
            listOf("full", "recent:1").flatMap { p ->
                listOf("kael-dark", "who-key", "kael-poison").flatMap { q -> (1..2).map { Triple(p, q, it) } }
            },
            result.samples.map { Triple(it.policy, it.probe.value, it.sample) },
        )
    }

    @Test
    fun `each sample is reported as it finishes, counting up to the total`() = runTest {
        val events = DefaultContinuityRun(FakeTextGenerator(), FakeJudge()).run(plan("full", samples = 1)).toList()

        val progress = events.filterIsInstance<RunEvent.SampleFinished>()
        assertEquals((1..3).toList(), progress.map { it.finished }.sorted())
        assertTrue(progress.all { it.total == 3 })
        assertIs<RunEvent.Finished>(events.last())
    }

    @Test
    fun `the prompt carries the policy's context taken right after the probe's turn`() = runTest {
        val generator = FakeTextGenerator()

        DefaultContinuityRun(generator, FakeJudge()).run(plan("full", "perceived/full", samples = 1)).toList()

        val voicePrompts = generator.requests.map { it.messages.filterIsInstance<ChatMessage.User>().single().text }
            .filter { "Who poisoned the wine?" in it }
        assertEquals(2, voicePrompts.size)
        assertEquals(
            1,
            voicePrompts.count {
                "You alone see the phial." in it
            },
            "only the omniscient policy shows Kael the secret",
        )
        val early = generator.requests.single { r ->
            r.messages.any { "Is the lighthouse lit?" in it.text } &&
                r.messages.any { "The story so far" in it.text }
        }
        assertFalse(early.messages.any { "brass key" in it.text }, "the probe after t1 sees nothing of t2")
    }

    @Test
    fun `the subject is called with its model and tuning, and the judge reads the answer`() = runTest {
        val generator = FakeTextGenerator { listOf(TextEvent.Delta(" Mara did it. "), completed()) }
        val judge = FakeJudge { _, fact -> if (fact.id == POISON) Verdict.Asserted else Verdict.Absent }

        val result = DefaultContinuityRun(
            generator,
            judge,
        ).run(plan("full", samples = 1)).filterIsInstance<RunEvent.Finished>().single().result

        assertTrue(generator.requests.all { it.model == SUBJECT.model && it.tuning == SUBJECT.tuning })
        assertTrue(judge.passages.all { it == "Mara did it." })
        val leak = result.samples.single { it.probe == ProbeId("kael-poison") }.outcome as SampleOutcome.Judged
        assertEquals(listOf(Verdict.Asserted, Verdict.Absent), leak.verdicts.map { it.verdict })
        assertEquals("0.0001", leak.usage?.costUsd)
    }

    @Test
    fun `a failed or empty answer is recorded, not judged`() = runTest {
        var calls = 0
        val generator = FakeTextGenerator {
            calls++
            if (calls == 1) {
                listOf(TextEvent.Failed(GenerationError.Access(DataError.Network)))
            } else {
                listOf(completed())
            }
        }
        val judge = FakeJudge()

        val result = DefaultContinuityRun(generator, judge).run(plan("full", samples = 1, concurrency = 1))
            .filterIsInstance<RunEvent.Finished>().single().result

        assertTrue(result.samples.all { it.outcome is SampleOutcome.Unanswered })
        assertTrue(judge.passages.isEmpty())
    }

    @Test
    fun `no more calls are in flight than the plan allows`() = runTest {
        val generator = FakeTextGenerator()

        DefaultContinuityRun(
            generator,
            FakeJudge(),
        ).run(plan("full", "recent:1", "chars:100", samples = 3, concurrency = 2)).toList()

        assertEquals(27, generator.requests.size)
        assertTrue(generator.mostInFlight.get() in 1..2, "at most two at once, saw ${generator.mostInFlight.get()}")
    }
}
