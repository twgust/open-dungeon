package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.DataError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratePremiseTest {

    private fun premise(vararg events: TextEvent) = ScriptedTextGenerator(events.toList()).let { generator ->
        generator to DefaultGeneratePremise(generator, DefaultSentenceBuilder())
    }

    @Test
    fun `the premise streams as it is written and ends written`() = runTest {
        val (_, generate) = premise(
            TextEvent.Delta("You owe "),
            TextEvent.Reasoning("thinking"),
            TextEvent.Delta("the salt."),
            TextEvent.Completed(usage = null, cutOff = false),
        )

        assertEquals(
            listOf(
                WritingProgress.Writing("You owe"),
                WritingProgress.Writing("You owe the salt."),
                WritingProgress.Written("You owe the salt."),
            ),
            generate(fullDraft, drafter).toList(),
        )
    }

    @Test
    fun `quotes around the whole premise come off, quotes inside it stay`() = runTest {
        val (_, wrapped) = premise(TextEvent.Delta("  \"You run.\"\n"), TextEvent.Completed(null, cutOff = false))
        val (_, quoting) = premise(
            TextEvent.Delta("\"Run,\" she says. \"Now.\""),
            TextEvent.Completed(null, cutOff = false),
        )

        assertEquals(WritingProgress.Written("You run."), wrapped(fullDraft, drafter).toList().last())
        assertEquals(
            WritingProgress.Written("\"Run,\" she says. \"Now.\""),
            quoting(fullDraft, drafter).toList().last(),
        )
    }

    @Test
    fun `a model that writes nothing has written nothing usable`() = runTest {
        val (_, generate) = premise(TextEvent.Delta("  \n"), TextEvent.Completed(null, cutOff = false))

        assertEquals(listOf(WritingProgress.Failed(DraftingError.Unusable)), generate(fullDraft, drafter).toList())
    }

    @Test
    fun `a failed request keeps its reason`() = runTest {
        val error = GenerationError.Access(DataError.InsufficientCredits("broke"))
        val (_, generate) = premise(TextEvent.Delta("You"), TextEvent.Failed(error))

        assertEquals(
            WritingProgress.Failed(DraftingError.Generation(error)),
            generate(fullDraft, drafter).toList().last(),
        )
    }

    @Test
    fun `the request goes to the drafting model, tuned, in the draft's session`() = runTest {
        val (generator, generate) = premise(TextEvent.Completed(null, cutOff = false))

        generate(fullDraft, drafter).toList()

        val request = generator.requests.single()
        assertEquals(drafter.model, request.model)
        assertEquals(drafter.tuning, request.tuning)
        assertEquals("draft-7", request.sessionId)
    }

    @Test
    fun `the prompt for a full draft`() {
        val prompt = PremisePrompt(StoryBrief(DefaultSentenceBuilder()))

        assertGolden("premise-full", prompt.messages(fullDraft).transcript())
    }

    @Test
    fun `the prompt for a reroll asks for something different`() {
        val prompt = PremisePrompt(StoryBrief(DefaultSentenceBuilder()))
        val draft = fullDraft.copy(premise = Phrase.Offered("You owe the salt-wardens a cargo you sank."))

        assertGolden("premise-reroll", prompt.messages(draft).transcript())
    }

    @Test
    fun `the prompt for a bare draft says only what was chosen`() {
        val prompt = PremisePrompt(StoryBrief(DefaultSentenceBuilder()))

        assertGolden("premise-bare", prompt.messages(StoryDraft(DraftId("bare"))).transcript())
    }
}
