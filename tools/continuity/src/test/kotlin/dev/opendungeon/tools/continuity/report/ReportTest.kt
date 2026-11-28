package dev.opendungeon.tools.continuity.report

import dev.opendungeon.tools.continuity.DARK
import dev.opendungeon.tools.continuity.JUDGE
import dev.opendungeon.tools.continuity.KEY_WITH_MARA
import dev.opendungeon.tools.continuity.POISON
import dev.opendungeon.tools.continuity.SUBJECT
import dev.opendungeon.tools.continuity.context.PolicySpec
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.run.JudgedFact
import dev.opendungeon.tools.continuity.run.ProbeSample
import dev.opendungeon.tools.continuity.run.RunPlan
import dev.opendungeon.tools.continuity.run.RunResult
import dev.opendungeon.tools.continuity.run.SampleOutcome
import dev.opendungeon.tools.continuity.run.SampleUsage
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.opendungeon.tools.continuity.scenario.ProbeId
import dev.opendungeon.tools.continuity.tinyScenario
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportTest {

    private val scenario = tinyScenario()

    private fun judged(
        policy: String,
        probe: String,
        sample: Int,
        vararg verdicts: Pair<FactId, Verdict>,
        tokens: Int = 100,
    ) = ProbeSample(
        policy = policy,
        probe = ProbeId(probe),
        sample = sample,
        promptCharacters = tokens * 4,
        outcome = SampleOutcome.Judged(
            answer = "answer",
            cutOff = false,
            usage = SampleUsage(promptTokens = tokens, completionTokens = 10, costUsd = "0.001"),
            verdicts = verdicts.map { (fact, verdict) -> JudgedFact(fact, verdict, quote = "quote ${fact.value}") },
        ),
    )

    private fun result(samples: List<ProbeSample>, vararg policies: String) = RunResult(
        RunPlan(
            scenario,
            policies.map {
                PolicySpec.parse(it).getOrThrow()
            },
            SUBJECT,
            JUDGE,
            samples = 2,
            concurrency = 1,
        ),
        samples,
    )

    @Test
    fun `checks carry their role, reader and distance`() {
        val checks = Scoring(scenario).checks(
            judged(
                "full",
                "kael-poison",
                1,
                POISON to Verdict.Asserted,
                DARK to Verdict.Contradicted,
            ),
        )

        val poison = checks.single { it.fact == POISON }
        assertEquals(Role.Forbid, poison.role)
        assertTrue(poison.byCharacter && poison.leaked && !poison.contradicted)
        assertEquals(2, poison.distance)
        val dark = checks.single { it.fact == DARK }
        assertEquals(Role.Guard, dark.role)
        assertTrue(dark.contradicted && !dark.recalled)
    }

    @Test
    fun `an answer passes only with every expected fact, no leak and no contradiction`() {
        val scoring = Scoring(scenario)

        assertEquals(true, scoring.passed(judged("full", "who-key", 1, KEY_WITH_MARA to Verdict.Asserted)))
        assertEquals(false, scoring.passed(judged("full", "who-key", 1, KEY_WITH_MARA to Verdict.Absent)))
        assertEquals(
            true,
            scoring.passed(
                judged(
                    "full",
                    "kael-poison",
                    1,
                    POISON to Verdict.Absent,
                    DARK to Verdict.Absent,
                ),
            ),
        )
        assertEquals(
            false,
            scoring.passed(
                judged(
                    "full",
                    "kael-poison",
                    1,
                    POISON to Verdict.Asserted,
                    DARK to Verdict.Absent,
                ),
            ),
        )
        assertEquals(
            null,
            scoring.passed(ProbeSample("full", ProbeId("who-key"), 1, 0, SampleOutcome.Unanswered("down"))),
        )
    }

    @Test
    fun `the report splits narrator recall, character recall, leaks and contradictions per policy`() {
        val samples = listOf(
            judged("full", "who-key", 1, KEY_WITH_MARA to Verdict.Asserted, tokens = 300),
            judged("full", "who-key", 2, KEY_WITH_MARA to Verdict.Contradicted, tokens = 300),
            judged("full", "kael-dark", 1, DARK to Verdict.Asserted, tokens = 300),
            judged("full", "kael-poison", 1, POISON to Verdict.Asserted, DARK to Verdict.Absent, tokens = 300),
            judged("recent:1", "who-key", 1, KEY_WITH_MARA to Verdict.Asserted, tokens = 100),
            ProbeSample(
                "recent:1",
                ProbeId("kael-poison"),
                1,
                400,
                SampleOutcome.Unjudged("answer", null, "judge down"),
            ),
        )

        val report = ContinuityReport.of(result(samples, "full", "recent:1"), createdAt = "now")

        val full = report.policies.single { it.policy == "full" }
        assertEquals(Rate.of(1, 2), full.narratorRecall)
        assertEquals(Rate.of(1, 1), full.characterRecall)
        assertEquals(Rate.of(1, 1), full.leaks)
        assertEquals(Rate.of(1, 4), full.contradictions, "who-key twice, kael-dark, and the guarded fact")
        assertEquals(Rate.of(2, 4), full.probesPassed)
        assertEquals(300, full.meanPromptTokens)
        assertEquals("0.004", full.costUsd)
        val recent = report.policies.single { it.policy == "recent:1" }
        assertEquals(1, recent.unjudged)
        assertEquals(Rate.of(0, 0), recent.leaks)
        assertEquals(listOf(FactId("key-with-mara"), POISON), report.incidents.map { it.fact })
        assertEquals(listOf(Incident.Kind.Contradiction, Incident.Kind.Leak), report.incidents.map { it.kind })
    }

    @Test
    fun `recall is bucketed by how far back the fact is`() {
        assertEquals(
            listOf(0, 0, 1, 1, 2, 3, 4, 5, 5),
            listOf(0, 3, 4, 10, 11, 31, 101, 301, 5_000).map(ContinuityReport::bucketOf),
        )

        val report = ContinuityReport.of(
            result(listOf(judged("full", "who-key", 1, KEY_WITH_MARA to Verdict.Asserted)), "full"),
            "now",
        )

        assertEquals(listOf("0–3", "4–10", "11–30", "31–100", "101–300", "301+"), report.distanceBuckets)
        assertEquals(Rate.of(1, 1), report.policies.single().recallByDistance[0])
        assertTrue(report.policies.single().recallByDistance.drop(1).all { it.isEmpty })
    }

    @Test
    fun `rates carry a Wilson interval`() {
        val rate = Rate.of(8, 10)

        assertEquals(0.8, rate.share)
        assertEquals(0.490, checkNotNull(rate.low), 0.001)
        assertEquals(0.943, checkNotNull(rate.high), 0.001)
        assertEquals(Rate(0, 0, null, null, null), Rate.of(0, 0))
        assertEquals(0.0, Rate.of(0, 5).low)
    }

    @Test
    fun `the page leads with the summary and lists every incident`() {
        val samples = listOf(judged("full", "kael-poison", 1, POISON to Verdict.Asserted, DARK to Verdict.Absent))

        val page = MarkdownReport.render(ContinuityReport.of(result(samples, "full"), "now"))

        assertTrue(page.startsWith("# Continuity: Tiny"))
        listOf("## Summary", "## Narrator recall by distance", "## Probes", "## Leaks and contradictions").forEach {
            assertTrue(it in page, "missing $it")
        }
        assertTrue("| `full` | `kael-poison` | 1 | `poison` | Leak | quote poison |" in page)
        assertTrue("| `kael-poison` | 0/1 |" in page)
    }
}
