package dev.opendungeon.tools.continuity.run

import dev.opendungeon.tools.continuity.DARK
import dev.opendungeon.tools.continuity.FakeJudge
import dev.opendungeon.tools.continuity.JUDGE
import dev.opendungeon.tools.continuity.POISON
import dev.opendungeon.tools.continuity.SUBJECT
import dev.opendungeon.tools.continuity.context.PolicySpec
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.scenario.ProbeId
import dev.opendungeon.tools.continuity.tinyScenario
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RejudgeRunTest {

    private val plan = RunPlan(
        tinyScenario(),
        listOf(PolicySpec.parse("full").getOrThrow()),
        SUBJECT,
        JUDGE,
        samples = 1,
        concurrency = 2,
    )

    private val usage = SampleUsage(promptTokens = 100, completionTokens = 10, costUsd = "0.001")

    private fun sample(probe: String, outcome: SampleOutcome) =
        ProbeSample("full", ProbeId(probe), sample = 1, promptCharacters = 400, outcome = outcome)

    @Test
    fun `answers are judged again, keeping what they were and what they cost`() = runTest {
        val judge = FakeJudge { _, fact -> if (fact.id == POISON) Verdict.Suspected else Verdict.Absent }
        val old = listOf(
            sample(
                "kael-poison",
                SampleOutcome.Judged(
                    "Mara, I'd guess.",
                    cutOff = true,
                    usage = usage,
                    verdicts = listOf(JudgedFact(POISON, Verdict.Absent, ""), JudgedFact(DARK, Verdict.Absent, "")),
                ),
            ),
            sample("kael-dark", SampleOutcome.Unjudged("It is dark.", usage, "judge down")),
        )

        val result = DefaultRejudgeRun(DefaultProbeJudge(judge)).rejudge(plan, old)
            .filterIsInstance<RunEvent.Finished>().single().result

        assertEquals(setOf("Mara, I'd guess.", "It is dark."), judge.passages.toSet())
        val poison = assertIs<SampleOutcome.Judged>(result.samples[0].outcome)
        assertEquals("Mara, I'd guess.", poison.answer)
        assertEquals(true, poison.cutOff)
        assertEquals(usage, poison.usage)
        assertEquals(Verdict.Suspected, poison.verdicts.single { it.fact == POISON }.verdict)
        assertIs<SampleOutcome.Judged>(result.samples[1].outcome)
        assertEquals(old.map { it.probe }, result.samples.map { it.probe }, "the run's order is kept")
    }

    @Test
    fun `an answer the model never gave is left as it was, and no judge is asked`() = runTest {
        val judge = FakeJudge()
        val unanswered = sample("who-key", SampleOutcome.Unanswered("down"))

        val result = DefaultRejudgeRun(DefaultProbeJudge(judge)).rejudge(plan, listOf(unanswered))
            .filterIsInstance<RunEvent.Finished>().single().result

        assertEquals(listOf(unanswered), result.samples)
        assertEquals(emptyList(), judge.passages)
    }
}
