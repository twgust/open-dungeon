package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.DataError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WriteOpeningTest {

    private fun opening(vararg events: TextEvent) = ScriptedTextGenerator(events.toList()).let { generator ->
        generator to DefaultWriteOpening(generator, DefaultSentenceBuilder())
    }

    private val done = TextEvent.Completed(usage = null, cutOff = false)

    private val castDraft = fullDraft.copy(
        premise = Phrase.Own("You owe the salt-wardens a cargo you sank."),
        cast = listOf(
            CastMember(
                Character(
                    id = CharacterId("maren"),
                    role = "reliquary keeper",
                    name = "Maren Kask",
                    appearance = "Ink-stained fingers and a coat two winters too thin.",
                    wants = "To buy back the relic her brother sold",
                    habit = "Counts the coins twice when she is lying",
                    voiceLine = "Saints don't haggle. I do.",
                    secret = Secret.Sealed("She forged the relic herself"),
                ),
                CastMember.Origin.Offered,
                inStory = true,
            ),
            CastMember(
                Character(id = CharacterId("ilse"), name = "Ilse Brandt", secret = Secret.Sealed("Informer")),
                CastMember.Origin.Offered,
                inStory = false,
            ),
        ),
    )

    @Test
    fun `a fresh opening streams as it is written and ends written`() = runTest {
        val (_, write) = opening(
            TextEvent.Delta("\n Rain "),
            TextEvent.Reasoning("thinking"),
            TextEvent.Delta("falls."),
            TextEvent.Delta("\n"),
            done,
        )

        assertEquals(
            listOf(
                WritingProgress.Writing("Rain"),
                WritingProgress.Writing("Rain falls."),
                WritingProgress.Written("Rain falls."),
            ),
            write(castDraft, drafter).toList(),
        )
    }

    @Test
    fun `a continuation is led by the space that joins it to the player's text`() = runTest {
        val (_, write) = opening(TextEvent.Delta("Maren is late."), done)

        assertEquals(
            WritingProgress.Written(" Maren is late."),
            write(castDraft.copy(opening = "You wait."), drafter).toList().last(),
        )
    }

    @Test
    fun `text that already ends in whitespace needs no space added`() = runTest {
        val (_, write) = opening(TextEvent.Delta(" Maren is late."), done)

        assertEquals(
            WritingProgress.Written("Maren is late."),
            write(castDraft.copy(opening = "You wait.\n"), drafter).toList().last(),
        )
    }

    @Test
    fun `a model that starts a new paragraph gets one`() = runTest {
        val (_, write) = opening(TextEvent.Delta("\nMaren is late."), done)

        assertEquals(
            WritingProgress.Written("\n\nMaren is late."),
            write(castDraft.copy(opening = "You wait."), drafter).toList().last(),
        )
    }

    @Test
    fun `a model that writes nothing has written nothing usable`() = runTest {
        val (_, write) = opening(TextEvent.Delta("  \n"), done)

        assertEquals(listOf(WritingProgress.Failed(DraftingError.Unusable)), write(castDraft, drafter).toList())
    }

    @Test
    fun `a failed request keeps its reason`() = runTest {
        val error = GenerationError.Access(DataError.InsufficientCredits("broke"))
        val (_, write) = opening(TextEvent.Delta("Rain"), TextEvent.Failed(error))

        assertEquals(
            listOf(
                WritingProgress.Writing("Rain"),
                WritingProgress.Failed(DraftingError.Generation(error)),
            ),
            write(castDraft, drafter).toList(),
        )
    }

    @Test
    fun `the request goes to the chosen model, tuned, in the draft's session`() = runTest {
        val (generator, write) = opening(done)

        write(castDraft, drafter).toList()

        val request = generator.requests.single()
        assertEquals(drafter.model, request.model)
        assertEquals(drafter.tuning, request.tuning)
        assertEquals("draft-7", request.sessionId)
    }

    @Test
    fun `no secret reaches the prompt, and nobody left out of the story does`() {
        val prompt = OpeningPrompt(StoryBrief(DefaultSentenceBuilder())).messages(castDraft).transcript()

        assertFalse("forged" in prompt)
        assertFalse("Ilse" in prompt)
    }

    @Test
    fun `the prompt for a fresh opening`() {
        val prompt = OpeningPrompt(StoryBrief(DefaultSentenceBuilder()))

        assertGolden("opening-fresh", prompt.messages(castDraft).transcript())
    }

    @Test
    fun `the prompt for continuing the player's text`() {
        val prompt = OpeningPrompt(StoryBrief(DefaultSentenceBuilder()))
        val draft = castDraft.copy(opening = "You wait under the tannery awning. The rain has not stopped in three")

        assertGolden("opening-continue", prompt.messages(draft).transcript())
    }

    @Test
    fun `the prompt for a bare draft says only what was chosen`() {
        val prompt = OpeningPrompt(StoryBrief(DefaultSentenceBuilder()))

        assertGolden("opening-bare", prompt.messages(StoryDraft(DraftId("bare"))).transcript())
    }
}
