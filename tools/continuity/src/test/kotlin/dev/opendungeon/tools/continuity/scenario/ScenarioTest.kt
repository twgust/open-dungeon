package dev.opendungeon.tools.continuity.scenario

import dev.opendungeon.tools.continuity.KAEL
import dev.opendungeon.tools.continuity.KEY_WITH_MARA
import dev.opendungeon.tools.continuity.KEY_WITH_YOU
import dev.opendungeon.tools.continuity.MARA
import dev.opendungeon.tools.continuity.POISON
import dev.opendungeon.tools.continuity.saltmere
import dev.opendungeon.tools.continuity.tinyScenario
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScenarioTest {

    @Test
    fun `the bundled scenario is sound`() {
        val scenario = saltmere()

        assertEquals(emptyList(), ScenarioCheck.problems(scenario))
        assertEquals(29, scenario.turns.size)
    }

    @Test
    fun `the bundled scenario probes every failure the harness measures`() {
        val probes = saltmere().probes

        assertTrue(probes.any { it.ask is Ask.Question && it.expect.isNotEmpty() }, "narrator recall")
        assertTrue(probes.any { it.ask is Ask.Action && it.guard.isNotEmpty() }, "continuity")
        assertTrue(probes.any { it.ask is Ask.Voice && it.forbid.isNotEmpty() }, "leaks")
        assertTrue(probes.any { it.ask is Ask.Voice && it.expect.isNotEmpty() }, "character recall")
    }

    @Test
    fun `a scenario survives a round trip through JSON`() {
        val scenario = tinyScenario()

        assertEquals(scenario, ScenarioCodec.decode(ScenarioCodec.encode(scenario)))
    }

    @Test
    fun `a misspelt key fails instead of dropping data`() {
        val text = ScenarioCodec.encode(tinyScenario()).replace("\"perceivedBy\"", "\"perceivedby\"")

        assertThrows<SerializationException> { ScenarioCodec.decode(text) }
    }

    @Test
    fun `a passage without perceivers is perceived by everyone present`() {
        val turn = tinyScenario().turns[1]

        assertEquals(setOf(KAEL, MARA), turn.narration[0].perceivers(turn.present))
        assertEquals(emptySet(), turn.narration[1].perceivers(turn.present))
        assertTrue(turn.perceivedBy(KAEL))
        assertFalse(tinyScenario().turns[2].perceivedBy(KAEL))
    }

    @Test
    fun `the timeline measures distance and knows what still holds`() {
        val scenario = tinyScenario()
        val timeline = Timeline(scenario)
        val probe = scenario.probes.single { it.id == ProbeId("who-key") }

        assertEquals(2, timeline.distance(checkNotNull(scenario.fact(POISON)), probe))
        assertFalse(timeline.isSuperseded(checkNotNull(scenario.fact(KEY_WITH_YOU)), TurnId("t2")))
        assertTrue(timeline.isSuperseded(checkNotNull(scenario.fact(KEY_WITH_YOU)), TurnId("t3")))
        assertEquals(
            listOf(KEY_WITH_MARA),
            timeline.currentFacts(TurnId("t3")).map {
                it.id
            }.filter { it.value.startsWith("key") },
        )
    }

    @Test
    fun `broken ids are reported`() {
        val scenario = tinyScenario().let { it.copy(turns = it.turns + it.turns.last()) }

        assertEquals(listOf("Turn id 't3' is used more than once"), ScenarioCheck.problems(scenario))
    }

    @Test
    fun `a perceiver who is not present is reported`() {
        val scenario = tinyScenario().let { s ->
            s.copy(
                turns = s.turns.mapIndexed { i, t ->
                    if (i ==
                        2
                    ) {
                        t.copy(narration = listOf(Passage("x", setOf(KAEL))))
                    } else {
                        t
                    }
                },
            )
        }

        assertEquals(
            listOf("A passage of turn 't2' is perceived by 'kael', who is not present"),
            ScenarioCheck.problems(scenario),
        )
    }

    @Test
    fun `a probe that relies on a fact too early or after it was replaced is reported`() {
        val scenario = tinyScenario().let { s ->
            s.copy(
                probes = listOf(
                    Probe(ProbeId("early"), TurnId("t0"), Ask.Question("q"), expect = setOf(POISON)),
                    Probe(ProbeId("stale"), TurnId("t3"), Ask.Question("q"), expect = setOf(KEY_WITH_YOU)),
                ),
            )
        }

        assertEquals(
            listOf(
                "Probe 'early' relies on 'poison' before it is established",
                "Probe 'stale' expects 'key-with-you', which is superseded by then",
            ),
            ScenarioCheck.problems(scenario),
        )
    }

    @Test
    fun `a fact superseded by one that came first is reported`() {
        val scenario = tinyScenario().let { s ->
            s.copy(facts = s.facts.map { if (it.id == POISON) it.copy(supersededBy = FactId("dark")) else it })
        }

        assertEquals(
            listOf("Fact 'poison' is superseded by 'dark', which is not established after it"),
            ScenarioCheck.problems(scenario),
        )
    }

    @Test
    fun `a voice for someone outside the cast and a probe with no facts are reported`() {
        val scenario = tinyScenario().let { s ->
            s.copy(probes = listOf(Probe(ProbeId("ghost"), TurnId("t1"), Ask.Voice(CastId("fenn"), "Hello?"))))
        }

        assertEquals(
            listOf("Probe 'ghost' checks no fact", "Probe 'ghost' speaks to 'fenn', who is not in the cast"),
            ScenarioCheck.problems(scenario),
        )
    }

    @Test
    fun `only the opening may lack a player line`() {
        val scenario = tinyScenario().let { s ->
            s.copy(
                turns = s.turns.mapIndexed { i, t ->
                    if (i ==
                        2
                    ) {
                        t.copy(player = null)
                    } else {
                        t
                    }
                },
            )
        }

        assertEquals(
            listOf("Turn 't2' has no player line; only the opening may lack one"),
            ScenarioCheck.problems(scenario),
        )
    }
}
