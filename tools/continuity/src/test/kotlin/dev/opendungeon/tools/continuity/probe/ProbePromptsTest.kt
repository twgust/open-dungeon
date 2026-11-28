package dev.opendungeon.tools.continuity.probe

import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.tools.continuity.FakeTextGenerator
import dev.opendungeon.tools.continuity.KAEL
import dev.opendungeon.tools.continuity.SUBJECT
import dev.opendungeon.tools.continuity.completed
import dev.opendungeon.tools.continuity.context.ContextBlock
import dev.opendungeon.tools.continuity.context.Reader
import dev.opendungeon.tools.continuity.scenario.ProbeId
import dev.opendungeon.tools.continuity.tinyScenario
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProbePromptsTest {

    private val scenario = tinyScenario()
    private val context = listOf(ContextBlock("The story so far", "[Story]\nThe lighthouse is dark."))

    private fun probe(id: String) = scenario.probes.single { it.id == ProbeId(id) }

    @Test
    fun `a voice is read by its speaker, everything else by the narrator`() {
        assertEquals(Reader.Character(KAEL), ProbePrompts.readerOf(probe("kael-poison")))
        assertEquals(Reader.Narrator, ProbePrompts.readerOf(probe("who-key")))
    }

    @Test
    fun `the ask a judge reads carries the question or the line, and none of the story`() {
        assertEquals("The question: Who has the key?", ProbePrompts.ask(scenario, probe("who-key")))
        assertEquals(
            "You say to Kael: \"Who poisoned the wine?\" The passage is the answer.",
            ProbePrompts.ask(scenario, probe("kael-poison")),
        )
    }

    @Test
    fun `every prompt carries the setup, the context and the ask`() {
        listOf("kael-poison", "who-key").forEach { id ->
            val messages = ProbePrompts.messages(scenario, probe(id), context)
            val user = messages.filterIsInstance<ChatMessage.User>().single().text

            assertTrue(messages.first() is ChatMessage.System)
            assertTrue(user.startsWith("Premise: A courier in a port town."))
            assertTrue("- Kael. A guide with a limp." in user)
            assertTrue("The story so far:\n[Story]\nThe lighthouse is dark." in user)
        }
    }

    @Test
    fun `a voice prompt speaks to the character and tells them what they can know`() {
        val messages = ProbePrompts.messages(scenario, probe("kael-poison"), context)

        assertTrue("You voice Kael" in messages.first().text)
        assertTrue("Kael knows only what they saw" in messages.first().text)
        assertTrue(messages.last().text.endsWith("You say to Kael: \"Who poisoned the wine?\"\n\nWrite Kael's answer."))
    }

    @Test
    fun `a completion collects the text and keeps the usage`() = runTest {
        val generator =
            FakeTextGenerator {
                listOf(TextEvent.Delta(" Mara "), TextEvent.Reasoning("hmm"), TextEvent.Delta("did."), completed(42))
            }

        val completion = generator.complete(TextRequest(SUBJECT.model, listOf(ChatMessage.User("q"))))

        assertEquals(Completion.Answered("Mara did.", completed(42).usage, cutOff = false), completion)
    }
}
