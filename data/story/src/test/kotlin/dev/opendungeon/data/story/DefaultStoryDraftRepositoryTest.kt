package dev.opendungeon.data.story

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.opendungeon.core.database.OpenDungeonDatabase
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.Take
import dev.opendungeon.domain.story.TakeId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DefaultStoryDraftRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        OpenDungeonDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val repository = DefaultStoryDraftRepository(database.storyDraftDao())

    @After
    fun close() = database.close()

    @Test
    fun `a full draft reads back exactly as written`() = runTest {
        repository.save(fullDraft)

        assertEquals(fullDraft, repository.openDraft())
    }

    @Test
    fun `saving again replaces the cast and takes rather than adding to them`() = runTest {
        repository.save(fullDraft)
        val trimmed = fullDraft.copy(
            step = CreationStep.Cast,
            cast = fullDraft.cast.take(1),
            takes = emptyList(),
            chosenTake = null,
        )

        repository.save(trimmed)

        assertEquals(trimmed, repository.openDraft())
    }

    @Test
    fun `a new draft replaces the open one`() = runTest {
        repository.save(fullDraft)
        val next = StoryDraft(id = DraftId("draft-2"))

        repository.save(next)

        assertEquals(next, repository.openDraft())
        repository.observe(fullDraft.id).test { assertNull(awaitItem()) }
    }

    @Test
    fun `discarding removes the draft and everything in it`() = runTest {
        repository.save(fullDraft)

        repository.discard(fullDraft.id)

        assertNull(repository.openDraft())
        assertEquals(0, database.query("SELECT * FROM draft_character", null).use { it.count })
        assertEquals(0, database.query("SELECT * FROM draft_take", null).use { it.count })
    }

    @Test
    fun `observers see each save`() = runTest {
        repository.observe(fullDraft.id).test {
            assertNull(awaitItem())
            repository.save(fullDraft)
            assertEquals(fullDraft, awaitItem())
            repository.save(fullDraft.copy(firstLine = "I lie."))
            assertEquals("I lie.", awaitItem()?.firstLine)
        }
    }

    @Test
    fun `a take that was still writing comes back interrupted`() = runTest {
        val writing = Take(TakeId("take-3"), ModelId("z-ai/glm-4.6"), "The bell", Take.State.Writing)
        repository.save(fullDraft.copy(takes = listOf(writing)))

        assertEquals(Take.State.Interrupted, repository.openDraft()?.takes?.single()?.state)
    }

    private companion object {
        val fullDraft = StoryDraft(
            id = DraftId("draft-1"),
            step = CreationStep.FirstTurn,
            genres = listOf(Phrase.Offered("Gothic"), Phrase.Own("salt-punk")),
            twist = Phrase.Offered("a heist"),
            protagonist = Character(
                id = CharacterId("me"),
                role = "a reliquary thief",
                name = "Ilse",
                personality = listOf("patient", "wry"),
                secret = Secret.Known("She sold her brother's name."),
            ),
            tone = Phrase.Own("hushed and cruel"),
            setting = Phrase.Offered("a drowned cathedral city"),
            limits = ContentLimits(ContentLimits.Desire.AllTheWay, ContentLimits.Violence.Shown, "no harm to animals"),
            premise = Phrase.Own("Steal the saint's bones back before the tide."),
            cast = listOf(
                CastMember(
                    character = Character(
                        id = CharacterId("c1"),
                        name = "Maren Kask",
                        wants = "Her mother's bones back.",
                        habit = "counts the coins twice when she is lying",
                        secret = Secret.Sealed("She is the one who sold them."),
                    ),
                    origin = CastMember.Origin.Offered,
                    inStory = true,
                ),
                CastMember(
                    character = Character(id = CharacterId("c2"), name = "Father Oszkar", voiceLine = "Kneel, or swim."),
                    origin = CastMember.Origin.Own,
                    inStory = false,
                ),
            ),
            firstLine = "I come in through the flooded nave.",
            takes = listOf(
                Take(TakeId("take-1"), ModelId("nous/hermes-4-405b"), "Water to the knee.", Take.State.Written),
                Take(TakeId("take-2"), ModelId("deepseek/deepseek-v3.2"), "The candles", Take.State.Interrupted),
            ),
            chosenTake = TakeId("take-1"),
        )
    }
}
