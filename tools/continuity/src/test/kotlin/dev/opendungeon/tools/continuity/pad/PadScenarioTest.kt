package dev.opendungeon.tools.continuity.pad

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.tools.continuity.FakeJudge
import dev.opendungeon.tools.continuity.FakeStructuredGenerator
import dev.opendungeon.tools.continuity.JUDGE
import dev.opendungeon.tools.continuity.KEY_WITH_MARA
import dev.opendungeon.tools.continuity.KEY_WITH_YOU
import dev.opendungeon.tools.continuity.SUBJECT
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.opendungeon.tools.continuity.scenario.ScenarioCheck
import dev.opendungeon.tools.continuity.scenario.TurnId
import dev.opendungeon.tools.continuity.tinyScenario
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PadScenarioTest {

    private fun interludes(count: Int): Array<Outcome<Any, GenerationError>> =
        Array(count) { Outcome.Success(InterludePayload("I wait $it.", "You wait, and time passes ($it).")) }

    private fun request(target: Int, concurrency: Int = 1) =
        PadRequest(tinyScenario(), targetTurns = target, writer = SUBJECT, judge = JUDGE, concurrency = concurrency)

    @Test
    fun `interludes are spread evenly, the extra ones late`() {
        assertEquals(listOf(0, 0, 0), PadPlan.spread(keyTurns = 4, targetTurns = 4))
        assertEquals(listOf(2, 2, 2), PadPlan.spread(keyTurns = 4, targetTurns = 10))
        assertEquals(listOf(2, 3, 3), PadPlan.spread(keyTurns = 4, targetTurns = 12))
        assertThrows<IllegalArgumentException> { PadPlan.spread(keyTurns = 4, targetTurns = 3) }
    }

    @Test
    fun `a padded scenario keeps its facts and probes on the hand-written turns`() = runTest {
        val pad = DefaultPadScenario(FakeStructuredGenerator(*interludes(6)), FakeJudge())

        val events = pad.pad(request(target = 10)).toList()

        val padded = assertIs<PadEvent.Finished>(events.last()).scenario
        assertEquals("tiny-10", padded.id)
        assertEquals(10, padded.turns.size)
        assertEquals(
            listOf("t0", "t0~1", "t0~2", "t1", "t1~1", "t1~2", "t2", "t2~1", "t2~2", "t3"),
            padded.turns.map {
                it.id.value
            },
        )
        assertTrue(
            padded.turns.filter { it.interlude }.all {
                it.present ==
                    padded.turns.first { k -> k.id.value == it.id.value.substringBefore('~') }.present
            },
        )
        assertEquals(tinyScenario().facts, padded.facts)
        assertEquals(tinyScenario().probes, padded.probes)
        assertEquals(emptyList(), ScenarioCheck.problems(padded))
        assertEquals(6, events.count { it is PadEvent.InterludeAccepted })
    }

    @Test
    fun `the writer sees what holds now and the turn the story must still reach`() = runTest {
        val generator = FakeStructuredGenerator(*interludes(1))

        DefaultPadScenario(generator, FakeJudge()).pad(request(target = 5)).toList()

        val prompt = generator.requests.single().messages.filterIsInstance<ChatMessage.User>().single().text
        assertTrue("You have the brass key." in prompt, "t2 holds after the third gap")
        assertTrue("\"I give Mara the key.\"" in prompt)
        assertTrue("interlude 1 of 1" in prompt)
    }

    @Test
    fun `an interlude that states a fact is written again, with the reason`() = runTest {
        val generator = FakeStructuredGenerator(*interludes(2))
        var judged = 0
        val judge = FakeJudge { _, fact ->
            if (fact.id == KEY_WITH_YOU &&
                judged++ == 0
            ) {
                Verdict.Asserted
            } else {
                Verdict.Absent
            }
        }

        val events = DefaultPadScenario(generator, judge).pad(request(target = 5)).toList()

        assertIs<PadEvent.Finished>(events.last())
        val retry = generator.requests[1].messages.filterIsInstance<ChatMessage.User>().single().text
        assertTrue("Earlier attempts were turned down" in retry && "You have the brass key." in retry)
    }

    @Test
    fun `contradicting a fact that no longer holds is fine, contradicting one that holds is not`() = runTest {
        // Here the key reaches Mara in t1, so by the only gap padded (after t2) the old fact no longer holds.
        val early = tinyScenario().let { s ->
            s.copy(
                facts = s.facts.map {
                    when (it.id) {
                        KEY_WITH_YOU -> it.copy(establishedIn = TurnId("t0"))
                        KEY_WITH_MARA -> it.copy(establishedIn = TurnId("t1"))
                        else -> it
                    }
                },
            )
        }

        fun contradicting(fact: FactId) = FakeJudge { _, checked ->
            if (checked.id ==
                fact
            ) {
                Verdict.Contradicted
            } else {
                Verdict.Absent
            }
        }

        val stale = DefaultPadScenario(FakeStructuredGenerator(*interludes(1)), contradicting(KEY_WITH_YOU))
            .pad(request(target = 5).copy(scenario = early)).toList()
        val current = DefaultPadScenario(FakeStructuredGenerator(*interludes(3)), contradicting(KEY_WITH_MARA))
            .pad(request(target = 5).copy(scenario = early)).toList()

        assertIs<PadEvent.Finished>(stale.last())
        assertIs<PadEvent.Failed>(current.last())
    }

    @Test
    fun `a gap that fails three times stops the padding`() = runTest {
        val judge = FakeJudge { _, _ -> Verdict.Asserted }

        val events = DefaultPadScenario(
            FakeStructuredGenerator(*interludes(3)),
            judge,
        ).pad(request(target = 5)).toList()

        val failed = assertIs<PadEvent.Failed>(events.single())
        assertEquals(TurnId("t2"), failed.after)
        assertEquals(3, failed.reasons.size)
    }

    @Test
    fun `an already padded scenario is refused`() {
        val padded = tinyScenario().let { s ->
            s.copy(turns = s.turns.map { it.copy(interlude = it.id.value == "t2") })
        }

        assertThrows<IllegalArgumentException> { PadRequest(padded, 10, SUBJECT, JUDGE, 1) }
    }
}
