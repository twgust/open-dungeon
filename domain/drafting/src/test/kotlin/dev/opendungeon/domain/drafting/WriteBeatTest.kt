package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.DataError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriteBeatTest {

    private fun writer(vararg events: TextEvent) = ScriptedTextGenerator(events.toList()).let { generator ->
        generator to DefaultWriteBeat(generator, BundledNamingGuide(), DefaultSentenceBuilder())
    }

    private val done = TextEvent.Completed(usage = null, cutOff = false)

    private val maren = Character(
        id = CharacterId("maren"),
        role = "reliquary keeper",
        name = "Maren Kask",
        wants = "To buy back the relic her brother sold",
        habit = "Counts the coins twice when she is lying",
        secret = Secret.Sealed("She forged the relic herself"),
    )

    private val story = Story(
        id = StoryId("story-3"),
        setup = StorySetup(
            genres = listOf(Phrase.Offered("Gothic")),
            twist = Phrase.Offered("a heist"),
            setting = Phrase.Offered("a salt-war border town"),
            premise = Phrase.Own("You owe the salt-wardens a cargo you sank."),
            cast = listOf(maren),
        ),
        beats = listOf(
            Beat(BeatId("b1"), Beat.Kind.Opening, "Rain falls on the salt flats and does not wash them."),
            Beat(BeatId("b2"), Beat.Kind.Turn, "You lay your hand over hers, on the key."),
            Beat(BeatId("b3"), Beat.Kind.Narration, "Maren does not pull away."),
        ),
    )

    private val prompt = BeatPrompt(StoryBrief(DefaultSentenceBuilder()), BundledNamingGuide())

    @Test
    fun `a beat streams as it is written and ends written`() = runTest {
        val (_, write) = writer(
            TextEvent.Delta("\n The key "),
            TextEvent.Reasoning("thinking"),
            TextEvent.Delta("is cold."),
            TextEvent.Delta("\n"),
            done,
        )

        assertEquals(
            listOf(
                WritingProgress.Writing("The key"),
                WritingProgress.Writing("The key is cold."),
                WritingProgress.Written("The key is cold."),
            ),
            write(story, BeatAsk.Next(), drafter).toList(),
        )
    }

    @Test
    fun `quotes around the whole beat come off, but dialogue keeps its marks`() = runTest {
        val (_, bare) = writer(TextEvent.Delta("\"The key is cold.\""), done)
        assertEquals(WritingProgress.Written("The key is cold."), bare(story, BeatAsk.Next(), drafter).toList().last())

        val (_, spoken) = writer(TextEvent.Delta("\"Go,\" she says. \"Now.\""), done)
        assertEquals(
            WritingProgress.Written("\"Go,\" she says. \"Now.\""),
            spoken(story, BeatAsk.Next(), drafter).toList().last(),
        )
    }

    @Test
    fun `a beat longer than allowed is cut`() = runTest {
        val (_, write) = writer(TextEvent.Delta("a".repeat(Story.MAX_BEAT_LENGTH + 50)), done)

        val written = write(story, BeatAsk.Next(), drafter).toList().last() as WritingProgress.Written
        assertEquals(Story.MAX_BEAT_LENGTH, written.text.length)
    }

    @Test
    fun `a model that writes nothing has written nothing usable`() = runTest {
        val (_, write) = writer(TextEvent.Delta("  \n"), done)

        assertEquals(
            listOf(WritingProgress.Failed(DraftingError.Unusable)),
            write(story, BeatAsk.Next(), drafter).toList(),
        )
    }

    @Test
    fun `a failed request keeps its reason`() = runTest {
        val error = GenerationError.Access(DataError.Network)
        val (_, write) = writer(TextEvent.Delta("The key"), TextEvent.Failed(error))

        assertEquals(
            listOf(WritingProgress.Writing("The key"), WritingProgress.Failed(DraftingError.Generation(error))),
            write(story, BeatAsk.Next(), drafter).toList(),
        )
    }

    @Test
    fun `the request goes to the chosen model, tuned, in the story's session`() = runTest {
        val (generator, write) = writer(done)

        write(story, BeatAsk.Next(), drafter).toList()

        val request = generator.requests.single()
        assertEquals(drafter.model, request.model)
        assertEquals(drafter.tuning, request.tuning)
        assertEquals("story-3", request.sessionId)
    }

    @Test
    fun `only a beat a model wrote is written again`() = runTest {
        val (_, write) = writer(done)
        val onTurn = story.copy(beats = story.beats.dropLast(1))

        assertFailsWith<IllegalArgumentException> { write(onTurn, BeatAsk.Again(), drafter).toList() }
    }

    @Test
    fun `no secret reaches the prompt`() {
        assertFalse("forged" in prompt.messages(story, BeatAsk.Next()).transcript())
    }

    @Test
    fun `writing again leaves the turned-down beat out of the story and quotes it as turned down`() {
        val transcript = prompt.messages(story, BeatAsk.Again(reason = "Too neat.")).transcript()

        assertEquals(1, Regex("Maren does not pull away").findAll(transcript).count())
        assertTrue("turned down this version" in transcript)
    }

    @Test
    fun `the prompt for the next beat`() {
        assertGolden("beat-next", prompt.messages(story, BeatAsk.Next()).transcript())
    }

    @Test
    fun `the prompt for the next beat with a direction`() {
        assertGolden(
            "beat-guided",
            prompt.messages(story, BeatAsk.Next(guide = "Maren lies about the coins.")).transcript(),
        )
    }

    @Test
    fun `the prompt for writing the last beat again`() {
        assertGolden("beat-again", prompt.messages(story, BeatAsk.Again()).transcript())
    }

    @Test
    fun `the prompt for writing the last beat again for a reason, with a direction`() {
        val ask = BeatAsk.Again(guide = "Keep it in the rain.", reason = "Too neat. She gives the answer away.")
        assertGolden("beat-again-reason", prompt.messages(story, ask).transcript())
    }

    @Test
    fun `a story with no choices still makes a prompt`() {
        val bare = Story(StoryId("s"), StorySetup(), listOf(Beat(BeatId("b"), Beat.Kind.Opening, "It begins.")))
        assertGolden("beat-bare", prompt.messages(bare, BeatAsk.Next()).transcript())
    }

    @Test
    fun `a long story carries its newest beats and says the earlier ones are left out`() {
        val long = story.copy(
            beats = (1..40).map { Beat(BeatId("b$it"), Beat.Kind.Narration, "Beat $it. " + "x".repeat(500)) },
        )
        val transcript = prompt.messages(long, BeatAsk.Next()).transcript()

        assertTrue("its earlier beats left out" in transcript)
        assertTrue("Beat 40." in transcript)
        assertFalse("Beat 1." in transcript)
    }
}

class RecentBeatsTest {

    private fun beat(n: Int, length: Int) = Beat(BeatId("b$n"), Beat.Kind.Narration, "x".repeat(length))

    @Test
    fun `everything fits a short story`() {
        val beats = (1..3).map { beat(it, 10) }
        assertEquals(beats, RecentBeats.of(beats, budget = 100))
    }

    @Test
    fun `the newest beats are kept, oldest first, up to the budget`() {
        val beats = (1..5).map { beat(it, 40) }
        assertEquals(beats.takeLast(2), RecentBeats.of(beats, budget = 100))
    }

    @Test
    fun `the newest beat goes in however long it is`() {
        val beats = listOf(beat(1, 10), beat(2, 500))
        assertEquals(beats.takeLast(1), RecentBeats.of(beats, budget = 100))
    }

    @Test
    fun `no beats, no window`() {
        assertEquals(emptyList(), RecentBeats.of(emptyList(), budget = 100))
    }
}
