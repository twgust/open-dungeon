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
import dev.opendungeon.domain.story.StorySummary
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
    fun `stories are listed the one played last first, and follow every save`() = runTest {
        assertEquals(emptyList(), repository.stories.first())
        repository.begin(salt, from = DraftId("none"))
        // The same people in another story, as when a story is begun twice from one cast.
        val other = salt.copy(id = StoryId("other"), beats = listOf(Beat(BeatId("o1"), Beat.Kind.Opening, "Snow.")))
        assertEquals(Outcome.Success(Unit), repository.begin(other, from = DraftId("none")))
        assertEquals(other, repository.story(other.id).value())
        assertEquals(listOf(other.id, salt.id), repository.stories.first().map(StorySummary::id))

        repository.save(salt.copy(unsent = Unsent(turn = "I wait.")))

        val listed = repository.stories.first()
        assertEquals(listOf(salt.id, other.id), listed.map(StorySummary::id))
        assertEquals(StorySummary(salt.id, null, salt.setup.genres, clock.last), listed.first())
    }

    @Test
    fun `a title is kept apart from play, so saves never write it and naming never replaces it`() = runTest {
        repository.begin(salt, from = DraftId("none"))
        val playedAt = repository.stories.first().single().playedAt

        repository.nameIfUntitled(salt.id, "The Salt Key")
        repository.nameIfUntitled(salt.id, "Brine")
        assertEquals("The Salt Key", repository.story(salt.id).value()?.title)
        assertEquals(playedAt, repository.stories.first().single().playedAt, "naming is not playing")

        repository.rename(salt.id, "Brine")
        // A copy of the story from before it was named, as play holds it.
        repository.save(salt.copy(guide = "Maren lies."))

        val stored = repository.story(salt.id).value()
        assertEquals("Brine", stored?.title)
        assertEquals("Maren lies.", stored?.guide)
    }

    @Test
    fun `a removed story is gone with its people and beats, and a late save does not bring it back`() = runTest {
        repository.begin(salt, from = DraftId("none"))

        assertEquals(Outcome.Success(Unit), repository.remove(salt.id))
        assertEquals(Outcome.Success(Unit), repository.remove(salt.id), "removing what is gone succeeds")

        assertNull(repository.story(salt.id).value())
        assertEquals(emptyList(), repository.stories.first())
        assertIs<Outcome.Failure<StorageError>>(repository.save(salt))
        assertNull(repository.story(salt.id).value())
        for (table in listOf("story", "story_character", "story_beat")) {
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { rows ->
                rows.moveToFirst()
                assertEquals(0, rows.getInt(0), "$table is empty")
            }
        }
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

        val last: Instant get() = Instant.fromEpochMilliseconds(millis)

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
