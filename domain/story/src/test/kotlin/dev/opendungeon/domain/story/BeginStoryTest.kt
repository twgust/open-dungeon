package dev.opendungeon.domain.story

import dev.opendungeon.core.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BeginStoryTest {

    private class RecordingStories(var fails: Boolean = false) : StoryRepository {
        val begun = mutableListOf<Pair<Story, DraftId>>()

        override suspend fun story(id: StoryId): Outcome<Story?, StorageError> = Outcome.Success(null)

        override val stories: Flow<List<StorySummary>> = flowOf(emptyList())

        override suspend fun begin(story: Story, from: DraftId): Outcome<Unit, StorageError> {
            if (fails) return Outcome.Failure(StorageError.Full)
            begun += story to from
            return Outcome.Success(Unit)
        }

        override suspend fun save(story: Story): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun rename(id: StoryId, title: String): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun nameIfUntitled(id: StoryId, title: String): Outcome<Unit, StorageError> =
            Outcome.Success(Unit)

        override suspend fun remove(id: StoryId): Outcome<Unit, StorageError> = Outcome.Success(Unit)
    }

    private class CountingIds : IdGenerator {
        private var next = 0

        override fun next(): String = "id-${++next}"
    }

    private val maren = Character(CharacterId("m"), name = "Maren Kask")
    private val ilse = Character(CharacterId("i"), name = "Ilse Brandt")

    private val draft = StoryDraft(
        id = DraftId("d"),
        genres = listOf(Phrase.Offered("Gothic")),
        premise = Phrase.Own("You owe the salt."),
        cast = listOf(
            CastMember(maren, CastMember.Origin.Offered, inStory = true),
            CastMember(ilse, CastMember.Origin.Offered, inStory = false),
        ),
        opening = "  Rain falls.\n",
    )

    @Test
    fun `a story begins from the draft's choices with its opening as the first beat`() = runTest {
        val stories = RecordingStories()

        val begun = DefaultBeginStory(stories, CountingIds())(draft)

        val (story, from) = stories.begun.single()
        assertEquals(Outcome.Success(story.id), begun)
        assertEquals(DraftId("d"), from)
        assertEquals(listOf(Beat(BeatId("id-2"), Beat.Kind.Opening, "Rain falls.")), story.beats)
        assertEquals(draft.genres, story.setup.genres)
        assertEquals(draft.premise, story.setup.premise)
        assertEquals(listOf(maren), story.setup.cast)
        assertEquals(Unsent(), story.unsent)
    }

    @Test
    fun `a story that cannot be stored did not begin`() = runTest {
        val stories = RecordingStories(fails = true)

        assertEquals(Outcome.Failure(StorageError.Full), DefaultBeginStory(stories, CountingIds())(draft))
        assertTrue(stories.begun.isEmpty())
    }

    @Test
    fun `a draft with no opening cannot begin`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            DefaultBeginStory(RecordingStories(), CountingIds())(draft.copy(opening = "   "))
        }
    }

    @Test
    fun `a story always has a beat`() {
        assertFailsWith<IllegalArgumentException> { Story(StoryId("s"), StorySetup(), emptyList()) }
    }
}
