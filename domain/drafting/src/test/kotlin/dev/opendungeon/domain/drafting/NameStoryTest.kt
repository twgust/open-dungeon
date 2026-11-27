package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.DefaultSentenceBuilder
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.testing.InMemoryStories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NameStoryTest {

    private val salt = Story(
        id = StoryId("salt"),
        setup = StorySetup.of(fullDraft).copy(premise = Phrase.Own("You owe the salt-wardens a cargo you sank.")),
        beats = listOf(
            Beat(BeatId("b1"), Beat.Kind.Opening, "Rain falls on the salt flats. Maren waits by the gate."),
            Beat(BeatId("b2"), Beat.Kind.Turn, "I wave."),
        ),
    )

    private class Drafter(private val model: DraftingModel?) : ResolveDraftingModel {
        override fun invoke(): Flow<DraftingModel?> = flowOf(model)
    }

    private suspend fun naming(
        vararg events: TextEvent,
        model: DraftingModel? = drafter,
        story: Story = salt,
    ): Triple<NameStory, InMemoryStories, ScriptedTextGenerator> {
        val stories = InMemoryStories()
        stories.begin(story, from = DraftId("draft"))
        val generator = ScriptedTextGenerator(events.toList())
        return Triple(
            DefaultNameStory(stories, Drafter(model), generator, DefaultSentenceBuilder()),
            stories,
            generator,
        )
    }

    private fun answer(text: String) = arrayOf(TextEvent.Delta(text), TextEvent.Completed(usage = null, cutOff = false))

    @Test
    fun `an untitled story is named with what the model wrote`() = runTest {
        val (name, stories, generator) = naming(*answer("The Salt "), TextEvent.Delta("Key"))

        assertEquals(Outcome.Success(Unit), name(salt.id))

        assertEquals("The Salt Key", stories.stored(salt.id)?.title)
        val request = generator.requests.single()
        assertEquals(drafter.model, request.model)
        assertEquals(drafter.tuning, request.tuning)
        assertEquals("salt", request.sessionId)
    }

    @Test
    fun `a story with a title, or none at all, is left as it is and nothing is asked`() = runTest {
        val (name, stories, generator) = naming(*answer("Other"), story = salt.copy(title = "Mine"))

        assertEquals(Outcome.Success(Unit), name(salt.id))
        assertEquals(Outcome.Success(Unit), name(StoryId("gone")))

        assertEquals("Mine", stories.stored(salt.id)?.title)
        assertTrue(generator.requests.isEmpty())
    }

    @Test
    fun `a name the player gives while the model writes is kept`() = runTest {
        val stories = InMemoryStories()
        stories.begin(salt, from = DraftId("draft"))
        val generator = object : TextGenerator {
            override fun stream(request: TextRequest): Flow<TextEvent> = flow {
                stories.rename(salt.id, "Mine")
                answer("Theirs").forEach { emit(it) }
            }
        }

        DefaultNameStory(stories, Drafter(drafter), generator, DefaultSentenceBuilder())(salt.id)

        assertEquals("Mine", stories.stored(salt.id)?.title)
    }

    @Test
    fun `without a model, or with an answer that is no title, the story stays untitled`() = runTest {
        val (noModel, stories, _) = naming(*answer("Title"), model = null)
        assertEquals(Outcome.Failure(NamingError.Drafting(DraftingError.NoModel)), noModel(salt.id))

        val (blank, blankStories, _) = naming(*answer("  \n \"\" "))
        assertEquals(Outcome.Failure(NamingError.Drafting(DraftingError.Unusable)), blank(salt.id))

        assertNull(stories.stored(salt.id)?.title)
        assertNull(blankStories.stored(salt.id)?.title)
    }

    @Test
    fun `a failed request or write keeps its reason`() = runTest {
        val error = GenerationError.Access(DataError.InsufficientCredits("broke"))
        val (failed, _, _) = naming(TextEvent.Delta("The"), TextEvent.Failed(error))
        assertEquals(Outcome.Failure(NamingError.Drafting(DraftingError.Generation(error))), failed(salt.id))

        val (refused, stories, _) = naming(*answer("The Salt Key"))
        stories.fail = 1
        assertEquals(Outcome.Failure(NamingError.Storage(StorageError.Full)), refused(salt.id))
    }

    @Test
    fun `wrapping models add is taken off, and a long title is cut at a word`() {
        assertEquals("The Salt Key", Titles.clean("Title: \"The Salt Key.\"\nA story of brine."))
        assertEquals("The Salt Key", Titles.clean("**The Salt Key**"))
        assertEquals("The Salt Key", Titles.clean("\n  # “The   Salt Key”  "))
        assertEquals("Mr. Grey's Salt", Titles.clean("Mr. Grey's Salt"))
        assertNull(Titles.clean("  \n\"\""))
        val long = Titles.clean("word ".repeat(30))!!
        assertTrue(long.length <= Story.MAX_TITLE_LENGTH)
        assertTrue(long.endsWith("word"))
    }

    @Test
    fun `the prompt names the story from its choices and how it begins`() {
        val prompt = TitlePrompt(StoryBrief(DefaultSentenceBuilder()))

        assertGolden("title", prompt.messages(salt).transcript())
    }
}
