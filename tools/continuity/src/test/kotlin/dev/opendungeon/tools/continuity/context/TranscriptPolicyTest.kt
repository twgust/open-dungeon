package dev.opendungeon.tools.continuity.context

import dev.opendungeon.tools.continuity.KAEL
import dev.opendungeon.tools.continuity.saltmere
import dev.opendungeon.tools.continuity.scenario.CastId
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.tinyScenario
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptPolicyTest {

    private suspend fun ContextPolicy.after(scenario: Scenario, turns: Int, reader: Reader): ContextBlock {
        val session = open(scenario)
        scenario.turns.take(turns).forEach { session.advance(it) }
        return session.contextFor(reader).single()
    }

    private fun policy(spec: String): ContextPolicy = PolicySpec.parse(spec).getOrThrow()

    @Test
    fun `full shows every turn, marked the way the app marks beats`() = runTest {
        val block = policy("full").after(tinyScenario(), turns = 2, Reader.Narrator)

        assertEquals("The story so far", block.heading)
        assertEquals(
            "[Story]\nThe lighthouse is dark.\n\n[The player's turn]\nI watch Mara.\n\n[Story]\nMara pours the wine. You alone see the phial.",
            block.body,
        )
    }

    @Test
    fun `recent keeps whole turns and says the earlier part is left out`() = runTest {
        val block = policy("recent:1").after(tinyScenario(), turns = 4, Reader.Narrator)

        assertEquals("The story so far, its earlier part left out", block.heading)
        assertEquals("[The player's turn]\nI give Mara the key.\n\n[Story]\nMara takes the brass key.", block.body)
    }

    @Test
    fun `chars keeps the newest lines within the budget, and always the newest one`() = runTest {
        val newestOnly = policy("chars:5").after(tinyScenario(), turns = 4, Reader.Narrator)
        val two = policy("chars:45").after(tinyScenario(), turns = 4, Reader.Narrator)

        assertEquals("[Story]\nMara takes the brass key.", newestOnly.body)
        assertEquals("[The player's turn]\nI give Mara the key.\n\n[Story]\nMara takes the brass key.", two.body)
    }

    @Test
    fun `an omniscient policy shows a character everything, secrets included`() = runTest {
        val block = policy("full").after(tinyScenario(), turns = 4, Reader.Character(KAEL))

        assertTrue("You alone see the phial." in block.body)
        assertTrue("brass key" in block.body)
    }

    @Test
    fun `a perceived policy shows a character only what they perceived`() = runTest {
        val block = policy("perceived/full").after(tinyScenario(), turns = 4, Reader.Character(KAEL))

        assertEquals("What Kael saw and heard", block.heading)
        assertEquals(
            "[Story]\nThe lighthouse is dark.\n\n[The player's turn]\nI watch Mara.\n\n[Story]\nMara pours the wine.",
            block.body,
        )
    }

    @Test
    fun `a perceived recent window counts only turns the character perceived`() = runTest {
        val block = policy("perceived/recent:1").after(tinyScenario(), turns = 4, Reader.Character(KAEL))

        assertEquals("[The player's turn]\nI watch Mara.\n\n[Story]\nMara pours the wine.", block.body)
    }

    @Test
    fun `a perceived policy still gives the narrator everything`() = runTest {
        val perceived = policy("perceived/full").after(tinyScenario(), turns = 4, Reader.Narrator)
        val full = policy("full").after(tinyScenario(), turns = 4, Reader.Narrator)

        assertEquals(full, perceived)
    }

    @Test
    fun `in the bundled story Kael never sees the poisoning or Oren's suspicion`() = runTest {
        val scenario = saltmere()
        val block = policy("perceived/full").after(scenario, turns = scenario.turns.size, Reader.Character(KAEL))

        assertFalse("green phial into Fenn's cup" in block.body)
        assertFalse("Between us, the harbourmaster" in block.body)
        assertTrue("Mara Quill poisoned the harbourmaster" in block.body, "he is told later")
    }

    @Test
    fun `none gives a reader the setup only, and says the story was left out`() = runTest {
        val narrator = policy("none").after(tinyScenario(), turns = 4, Reader.Narrator)
        val kael = policy("none").after(tinyScenario(), turns = 4, Reader.Character(KAEL))

        assertEquals(ContextBlock("The story so far, its earlier part left out", "Left out."), narrator)
        assertEquals(narrator, kael)
    }

    @Test
    fun `sessions do not share what they have seen`() = runTest {
        val policy = policy("full")
        val first = policy.open(tinyScenario())
        first.advance(tinyScenario().turns[0])

        val second = policy.open(tinyScenario()).contextFor(Reader.Narrator).single()

        assertEquals("Nothing yet.", second.body)
    }

    @Test
    fun `specs name their policy the way they were written`() {
        listOf("none", "full", "recent:3", "chars:12000", "perceived/full", "perceived/recent:6").forEach {
            assertEquals(it, policy(it).name)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["recent", "recent:0", "chars:-1", "everything", "perceived/", "recent:x"])
    fun `malformed specs are refused with a message`(spec: String) {
        val failure = PolicySpec.parse(spec).exceptionOrNull()

        assertTrue(failure is IllegalArgumentException && !failure.message.isNullOrBlank(), "$spec gave $failure")
    }

    @Test
    fun `the defaults all parse`() {
        assertEquals(PolicySpec.Defaults, PolicySpec.Defaults.map { policy(it).name })
    }

    @Test
    fun `a character missing from the cast is named by id`() = runTest {
        val block = policy("perceived/full").after(tinyScenario(), turns = 1, Reader.Character(CastId("nobody")))

        assertEquals("What nobody saw and heard", block.heading)
        assertEquals("Nothing yet.", block.body)
    }
}
