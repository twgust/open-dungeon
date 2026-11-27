package dev.opendungeon.feature.library.domain

import app.cash.turbine.test
import dev.opendungeon.domain.drafting.NameUntitledStories
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.testing.InMemoryStories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLibraryTest {

    private val salt = story("salt", title = "The Salt Key", genre = "Gothic")
    private val snow = story("snow", title = "Snowbound")

    /** Counts how often naming is started; naming itself is tested where it lives. */
    private class CountingNaming : NameUntitledStories {
        var started = 0

        override suspend fun invoke() {
            started++
            awaitCancellation()
        }
    }

    private class Setup(val library: Library, val stories: InMemoryStories, val naming: CountingNaming)

    /** Begins [stories] in order, so the last is the one played last. */
    private suspend fun TestScope.library(
        vararg stories: Story,
        prepare: suspend InMemoryStories.() -> Unit = {},
    ): Setup {
        val stored = InMemoryStories()
        stories.forEach { stored.begin(it, from = DraftId("draft")) }
        stored.prepare()
        val naming = CountingNaming()
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        return Setup(DefaultLibrary(scope, stored, naming, undoWindow = WINDOW), stored, naming)
    }

    private val Setup.shown get() = library.state.value.stories.map { it.title }

    @Test
    fun `stories are listed the one played last first, once read`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)

        assertFalse(setup.library.state.value.loading)
        assertEquals(listOf("Snowbound", "The Salt Key"), setup.shown)
        setup.stories.save(salt.copy(guide = "Maren lies."))
        assertEquals(listOf("The Salt Key", "Snowbound"), setup.shown)
    }

    @Test
    fun `opening empties the trash of earlier sessions and starts naming, once`() =
        runTest(UnconfinedTestDispatcher()) {
            val setup = library(salt, snow) { trash(StoryId("salt")) }

            assertNull(setup.stories.stored(salt.id), "trashed before this library, so gone for good")
            assertEquals(listOf("Snowbound"), setup.shown)
            assertEquals(1, setup.naming.started)
        }

    @Test
    fun `a title opens as it is, cut to its longest, and saves only when changed and not blank`() =
        runTest(UnconfinedTestDispatcher()) {
            val library = library(salt).library

            library.edit(salt.id)
            assertEquals(
                TitleEdit(salt.id, "The Salt Key", Story.MAX_TITLE_LENGTH, canSave = false),
                library.state.value.editing,
            )
            library.typeTitle("   ")
            assertFalse(library.state.value.editing!!.canSave)
            library.typeTitle(" The Salt Key ")
            assertFalse(library.state.value.editing!!.canSave, "the same title, spaced, is no change")
            library.typeTitle("x".repeat(Story.MAX_TITLE_LENGTH + 5))
            assertEquals(Story.MAX_TITLE_LENGTH, library.state.value.editing!!.text.length)
            assertTrue(library.state.value.editing!!.canSave)
        }

    @Test
    fun `an untitled story opens with an empty title`() = runTest(UnconfinedTestDispatcher()) {
        val library = library(story("a")).library

        library.edit(StoryId("a"))

        assertEquals("", library.state.value.editing?.text)
    }

    @Test
    fun `a saved title shows at once, trimmed, and is stored`() = runTest(UnconfinedTestDispatcher()) {
        val hold = CompletableDeferred<Unit>()
        val setup = library(salt)
        setup.stories.hold = hold
        setup.library.edit(salt.id)
        setup.library.typeTitle("  Brine  ")

        setup.library.saveTitle()

        assertNull(setup.library.state.value.editing)
        assertEquals(listOf("Brine"), setup.shown, "before storage has it")
        hold.complete(Unit)
        assertEquals("Brine", setup.stories.stored(salt.id)?.title)
        assertEquals(listOf("Brine"), setup.shown)
    }

    @Test
    fun `a title that cannot be stored is taken back, and says so`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt)
        setup.stories.fail = 1

        setup.library.failures.test {
            setup.library.edit(salt.id)
            setup.library.typeTitle("Brine")
            setup.library.saveTitle()

            assertEquals(LibraryFailure.Rename(StorageError.Full), awaitItem())
            assertEquals(listOf("The Salt Key"), setup.shown)
        }
    }

    @Test
    fun `verbs that mean nothing now change nothing`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt)

        setup.library.saveTitle()
        setup.library.undoDelete()
        setup.library.typeTitle("Brine")
        setup.library.delete(StoryId("elsewhere"))
        setup.library.edit(StoryId("elsewhere"))
        setup.library.edit(salt.id)
        setup.library.typeTitle("Brine")
        setup.library.cancelEdit()

        assertNull(setup.library.state.value.editing)
        assertEquals(listOf("The Salt Key"), setup.shown)
        assertEquals(listOf("begin salt", "empty trash"), setup.stories.writes)
    }

    @Test
    fun `a delete is trashed at once, closes its title, and undo brings it back where it was`() =
        runTest(UnconfinedTestDispatcher()) {
            val setup = library(salt, snow)
            setup.library.edit(salt.id)

            setup.library.delete(salt.id)

            assertNull(setup.library.state.value.editing)
            assertEquals(salt.id, setup.library.state.value.undo)
            assertEquals(listOf("Snowbound"), setup.shown)
            assertTrue(setup.stories.isTrashed(salt.id), "stored as deleted at once, so the app may die now")

            setup.library.undoDelete()

            assertNull(setup.library.state.value.undo)
            assertEquals(listOf("Snowbound", "The Salt Key"), setup.shown)
            assertFalse(setup.stories.isTrashed(salt.id))
        }

    @Test
    fun `an undo shows at once, before storage has it`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)
        setup.library.delete(salt.id)
        val hold = CompletableDeferred<Unit>()
        setup.stories.hold = hold

        setup.library.undoDelete()

        assertEquals(listOf("Snowbound", "The Salt Key"), setup.shown)
        hold.complete(Unit)
        assertEquals(listOf("Snowbound", "The Salt Key"), setup.shown)
    }

    @Test
    fun `undo is offered for the window, then the story stays deleted`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)
        setup.library.delete(salt.id)

        advanceTimeBy(WINDOW - 1.seconds)
        assertEquals(salt.id, setup.library.state.value.undo)
        advanceTimeBy(2.seconds)

        assertNull(setup.library.state.value.undo)
        setup.library.undoDelete()
        assertEquals(listOf("Snowbound"), setup.shown, "too late to take back")
        assertTrue(setup.stories.isTrashed(salt.id))
    }

    @Test
    fun `a second delete takes over the offer, and only it can be taken back`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)
        setup.library.delete(salt.id)
        advanceTimeBy(WINDOW - 1.seconds)

        setup.library.delete(snow.id)
        advanceTimeBy(2.seconds)

        assertEquals(snow.id, setup.library.state.value.undo, "a new delete gets the whole window")
        setup.library.undoDelete()
        assertEquals(listOf("Snowbound"), setup.shown)
        assertTrue(setup.stories.isTrashed(salt.id))
    }

    @Test
    fun `a delete storage refuses puts the story back, and says so`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)
        setup.stories.fail = 1

        setup.library.failures.test {
            setup.library.delete(salt.id)

            assertEquals(LibraryFailure.Delete(StorageError.Full), awaitItem())
            assertEquals(listOf("Snowbound", "The Salt Key"), setup.shown)
            assertNull(setup.library.state.value.undo)
        }
    }

    @Test
    fun `an undo storage refuses leaves the story deleted, and says so`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)
        setup.library.delete(salt.id)
        setup.stories.fail = 1

        setup.library.failures.test {
            setup.library.undoDelete()

            assertEquals(LibraryFailure.Undo(StorageError.Full), awaitItem())
            assertEquals(listOf("Snowbound"), setup.shown)
        }
    }

    private companion object {
        val WINDOW = 5.seconds

        fun story(id: String, title: String? = null, genre: String? = null) = Story(
            id = StoryId(id),
            setup = StorySetup(genres = listOfNotNull(genre?.let(Phrase::Offered))),
            beats = listOf(Beat(BeatId("$id-1"), Beat.Kind.Opening, "Rain falls.")),
            title = title,
        )
    }
}
