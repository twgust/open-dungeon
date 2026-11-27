package dev.opendungeon.data.story

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.opendungeon.core.database.OpenDungeonDatabase
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.Composer
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.Unsent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class DefaultStoryRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        OpenDungeonDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val clock = TickingClock()

    private val repository = DefaultStoryRepository(database.storyDao(), clock)

    @After
    fun close() = database.close()

    @Test
    fun `a begun story reads back exactly as written, and its draft is gone`() = runTest {
        val drafts = DefaultStoryDraftRepository(database.storyDraftDao())
        drafts.save(StoryDraft(id = DraftId("draft-1"), opening = "Rain falls."))

        repository.begin(salt, from = DraftId("draft-1"))

        assertEquals(salt, repository.story(salt.id).value())
        assertNull(drafts.openDraft().value())
    }

    @Test
    fun `a story that was never stored is missing`() = runTest {
        assertNull(repository.story(StoryId("elsewhere")).value())
    }

    @Test
    fun `saves keep beats, guide and what is unsent, in order`() = runTest {
        repository.begin(salt, from = DraftId("none"))
        val played = salt.copy(
            beats = salt.beats + Beat(BeatId("b4"), Beat.Kind.Turn, "I wait.") +
                Beat(BeatId("b5"), Beat.Kind.Narration, "The key is cold."),
            guide = "Maren lies.",
            unsent = Unsent(turn = "I pocket it", composer = Composer.Edit(BeatId("b2"), "You take the key.")),
        )

        repository.save(played)

        assertEquals(played, repository.story(salt.id).value())
    }

    @Test
    fun `a beat removed, rewritten or put back between saves is stored as it now stands`() = runTest {
        repository.begin(salt, from = DraftId("none"))
        val deleted = salt.copy(beats = salt.beats.filterNot { it.id == BeatId("b2") })
        repository.save(deleted)
        assertEquals(deleted, repository.story(salt.id).value())

        val rewritten = deleted.copy(beats = deleted.beats.dropLast(1) + deleted.beats.last().copy(text = "Again."))
        repository.save(rewritten)
        assertEquals(rewritten, repository.story(salt.id).value())

        repository.save(salt)
        assertEquals(salt, repository.story(salt.id).value())
    }

    @Test
    fun `a new repository, as after the app restarts, saves over what is stored`() = runTest {
        repository.begin(salt, from = DraftId("none"))
        val restarted = DefaultStoryRepository(database.storyDao(), clock)
        val shorter = salt.copy(beats = salt.beats.take(1), guide = "Keep it in the rain.")

        restarted.save(shorter)

        assertEquals(shorter, restarted.story(salt.id).value())
    }

    @Test
    fun `an open edit of a beat that is gone is dropped on reading`() = runTest {
        repository.begin(salt, from = DraftId("none"))
        repository.save(salt.copy(unsent = Unsent(composer = Composer.Edit(BeatId("gone"), "text"))))

        assertEquals(Unsent(), repository.story(salt.id).value()?.unsent)
    }

    @Test
    fun `latest follows the story saved last`() = runTest {
        assertNull(repository.latest.first())
        repository.begin(salt, from = DraftId("none"))
        assertEquals(salt.id, repository.latest.first())
        // The same people in another story, as when a story is begun twice from one cast.
        val other = salt.copy(id = StoryId("other"), beats = listOf(Beat(BeatId("o1"), Beat.Kind.Opening, "Snow.")))
        assertEquals(Outcome.Success(Unit), repository.begin(other, from = DraftId("none")))
        assertEquals(other, repository.story(other.id).value())
        assertEquals(other.id, repository.latest.first())
        repository.save(salt.copy(unsent = Unsent(turn = "I wait.")))
        assertEquals(salt.id, repository.latest.first())
    }

    @Test
    fun `a write the database refuses is a storage failure, and the stored story is untouched`() = runTest {
        repository.begin(salt, from = DraftId("none"))
        val sql = database.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER refuse BEFORE INSERT ON story_beat BEGIN SELECT RAISE(ABORT, 'refused'); END")
        val longer = salt.copy(
            beats = salt.beats + Beat(BeatId("b4"), Beat.Kind.Turn, "Never stored."),
            guide = "Never stored.",
        )

        val result = repository.save(longer)

        assertIs<StorageError.Failed>(assertIs<Outcome.Failure<StorageError>>(result).error)
        assertEquals(salt, repository.story(salt.id).value(), "the transaction rolled back whole")
        sql.execSQL("DROP TRIGGER refuse")
        repository.save(longer)
        assertEquals(longer, repository.story(salt.id).value())
    }

    /** Each reading is a millisecond after the last, so the story saved last is never tied. */
    private class TickingClock : Clock {
        private var millis = 1_700_000_000_000L

        override fun now(): Instant = Instant.fromEpochMilliseconds(++millis)
    }

    private companion object {
        val salt = Story(
            id = StoryId("salt"),
            setup = StorySetup(
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
                limits = ContentLimits(
                    ContentLimits.Desire.AllTheWay,
                    ContentLimits.Violence.Shown,
                    "no harm to animals",
                ),
                premise = Phrase.Own("Steal the saint's bones back before the tide."),
                cast = listOf(
                    Character(
                        id = CharacterId("c1"),
                        name = "Maren Kask",
                        wants = "Her mother's bones back.",
                        secret = Secret.Sealed("She is the one who sold them."),
                    ),
                    Character(id = CharacterId("c2"), name = "Father Oszkar", voiceLine = "Kneel, or swim."),
                ),
            ),
            beats = listOf(
                Beat(BeatId("b1"), Beat.Kind.Opening, "I come in through the flooded nave."),
                Beat(BeatId("b2"), Beat.Kind.Turn, "I light a candle."),
                Beat(BeatId("b3"), Beat.Kind.Narration, "The water stills around your knees."),
            ),
        )
    }

    private fun <T> Outcome<T, StorageError>.value(): T = assertIs<Outcome.Success<T>>(this).value
}
