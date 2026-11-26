package dev.opendungeon.data.story

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.opendungeon.core.database.OpenDungeonDatabase
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryDraft
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

        assertEquals(fullDraft, repository.openDraft().value())
    }

    @Test
    fun `saving again replaces the cast rather than adding to it`() = runTest {
        repository.save(fullDraft)
        val trimmed = fullDraft.copy(step = CreationStep.Cast, cast = fullDraft.cast.take(1), opening = "")

        repository.save(trimmed)

        assertEquals(trimmed, repository.openDraft().value())
    }

    @Test
    fun `a new draft replaces the open one`() = runTest {
        repository.save(fullDraft)
        val next = StoryDraft(id = DraftId("draft-2"))

        repository.save(next)

        assertEquals(next, repository.openDraft().value())
        assertNull(repository.draft(fullDraft.id).value())
    }

    @Test
    fun `discarding removes the draft and everything in it`() = runTest {
        repository.save(fullDraft)

        repository.discard(fullDraft.id)

        assertNull(repository.openDraft().value())
        assertEquals(0, database.query("SELECT * FROM draft_character", null).use { it.count })
    }

    @Test
    fun `a draft reads back by its id`() = runTest {
        repository.save(fullDraft)

        assertEquals(fullDraft, repository.draft(fullDraft.id).value())
        assertNull(repository.draft(DraftId("elsewhere")).value())
    }

    @Test
    fun `a write the database refuses is a storage failure, and the stored draft is untouched`() = runTest {
        repository.save(fullDraft)
        val member = fullDraft.cast.first()
        // Two people with one id break the primary key, so SQLite fails the transaction part-way.
        val broken = fullDraft.copy(opening = "Never stored.", cast = listOf(member, member))

        val result = repository.save(broken)

        assertIs<StorageError.Failed>(assertIs<Outcome.Failure<StorageError>>(result).error)
        assertEquals(fullDraft, repository.openDraft().value(), "the transaction rolled back whole")
    }

    private companion object {
        val fullDraft = StoryDraft(
            id = DraftId("draft-1"),
            step = CreationStep.Opening,
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
                    character = Character(
                        id = CharacterId("c2"),
                        name = "Father Oszkar",
                        voiceLine = "Kneel, or swim.",
                    ),
                    origin = CastMember.Origin.Own,
                    inStory = false,
                ),
            ),
            opening = "I come in through the flooded nave.\n\nWater to the knee, and the candles still lit.",
        )
    }

    private fun <T> Outcome<T, StorageError>.value(): T = assertIs<Outcome.Success<T>>(this).value
}
