package dev.opendungeon.tools.continuity.judge

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.tools.continuity.DARK
import dev.opendungeon.tools.continuity.FakeStructuredGenerator
import dev.opendungeon.tools.continuity.JUDGE
import dev.opendungeon.tools.continuity.POISON
import dev.opendungeon.tools.continuity.tinyScenario
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModelJudgeTest {

    private val facts = tinyScenario().facts.filter { it.id == DARK || it.id == POISON }

    @Test
    fun `verdicts come back per fact, ids read with or without brackets`() = runTest {
        val generator = FakeStructuredGenerator(
            Outcome.Success(
                JudgePayload(
                    listOf(
                        CheckPayload("[dark]", Verdict.Asserted, " the lamp is out "),
                        CheckPayload("poison", Verdict.Absent, ""),
                    ),
                ),
            ),
        )

        val result = ModelJudge(generator).judge("The lamp is out.", facts, "Wren", JUDGE)

        assertEquals(
            JudgeResult.Judged(
                mapOf(
                    DARK to FactVerdict(Verdict.Asserted, "the lamp is out"),
                    POISON to FactVerdict(Verdict.Absent, ""),
                ),
            ),
            result,
        )
    }

    @Test
    fun `the judge reads the passage and the facts, and is told who you are`() = runTest {
        val generator = FakeStructuredGenerator(
            Outcome.Success(JudgePayload(facts.map { CheckPayload(it.id.value, Verdict.Absent, "") })),
        )

        ModelJudge(generator).judge("A passage.", facts, "Wren", JUDGE)

        val request = generator.requests.single()
        val user = request.messages.filterIsInstance<ChatMessage.User>().single().text
        assertTrue("A passage." in user)
        assertTrue("- [dark] The lighthouse is dark." in user)
        assertTrue("player's character, Wren" in user)
        assertEquals(JUDGE.model, request.model)
        assertEquals(JUDGE.tuning, request.tuning)
    }

    @Test
    fun `a missing, repeated or unknown verdict makes the result untrustworthy`() = runTest {
        val payloads = listOf(
            listOf(CheckPayload("dark", Verdict.Asserted, "x")),
            listOf(
                CheckPayload("dark", Verdict.Asserted, "x"),
                CheckPayload("dark", Verdict.Absent, ""),
                CheckPayload("poison", Verdict.Absent, ""),
            ),
            listOf(
                CheckPayload("dark", Verdict.Asserted, "x"),
                CheckPayload("poison", Verdict.Absent, ""),
                CheckPayload("storm", Verdict.Absent, ""),
            ),
        )

        payloads.forEach { checks ->
            val result = ModelJudge(
                FakeStructuredGenerator(Outcome.Success(JudgePayload(checks))),
            ).judge("p", facts, "Wren", JUDGE)

            assertIs<JudgeFailure.Incomplete>((result as JudgeResult.Failed).reason)
        }
    }

    @Test
    fun `a generation failure is reported as such`() = runTest {
        val error = GenerationError.Access(DataError.RateLimited("slow down"))

        val result = ModelJudge(FakeStructuredGenerator(Outcome.Failure(error))).judge("p", facts, "Wren", JUDGE)

        assertEquals(JudgeResult.Failed(JudgeFailure.Generation(error)), result)
    }
}
