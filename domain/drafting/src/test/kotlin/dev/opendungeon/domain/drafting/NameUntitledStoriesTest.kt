package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.testing.InMemoryStories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NameUntitledStoriesTest {

    /** Names every story it is asked to, unless told to fail, and remembers whom it was asked about. */
    private class FakeNaming(private val stories: InMemoryStories) : NameStory {
        val asked = mutableListOf<StoryId>()
        var fails = false
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun invoke(id: StoryId): Outcome<Unit, NamingError> {
            asked += id
            gate?.await()
            if (fails) return Outcome.Failure(NamingError.Drafting(DraftingError.NoModel))
            stories.nameIfUntitled(id, "Named ${id.value}")
            return Outcome.Success(Unit)
        }
    }

    private fun story(id: String, title: String? = null) =
        Story(StoryId(id), StorySetup(), listOf(Beat(BeatId("$id-1"), Beat.Kind.Opening, "Rain.")), title = title)

    @Test
    fun `untitled stories are named one at a time, the one played last first`() = runTest(UnconfinedTestDispatcher()) {
        val stories = InMemoryStories()
        listOf(story("a"), story("b"), story("salt", "The Salt Key")).forEach { stories.begin(it, DraftId("d")) }
        val gate = CompletableDeferred<Unit>()
        val naming = FakeNaming(stories).apply { this.gate = gate }

        backgroundScope.launch { DefaultNameUntitledStories(stories, naming)() }

        assertEquals(listOf(StoryId("b")), naming.asked, "one at a time")
        gate.complete(Unit)
        assertEquals(listOf(StoryId("b"), StoryId("a")), naming.asked)
        assertEquals("Named a", stories.stored(StoryId("a"))?.title)
        assertEquals("The Salt Key", stories.stored(StoryId("salt"))?.title)
    }

    @Test
    fun `a story that cannot be named is not asked about again in the same run, but is in the next`() =
        runTest(UnconfinedTestDispatcher()) {
            val stories = InMemoryStories()
            stories.begin(story("a"), DraftId("d"))
            val naming = FakeNaming(stories).apply { fails = true }

            val run = backgroundScope.launch { DefaultNameUntitledStories(stories, naming)() }
            stories.save(story("a").copy(guide = "Again."))
            assertEquals(listOf(StoryId("a")), naming.asked)

            run.cancel()
            backgroundScope.launch { DefaultNameUntitledStories(stories, naming)() }
            assertEquals(listOf(StoryId("a"), StoryId("a")), naming.asked)
        }

    @Test
    fun `a story begun while it runs is named too`() = runTest(UnconfinedTestDispatcher()) {
        val stories = InMemoryStories()
        val naming = FakeNaming(stories)
        backgroundScope.launch { DefaultNameUntitledStories(stories, naming)() }

        stories.begin(story("new"), DraftId("d"))

        assertEquals("Named new", stories.stored(StoryId("new"))?.title)
    }
}
