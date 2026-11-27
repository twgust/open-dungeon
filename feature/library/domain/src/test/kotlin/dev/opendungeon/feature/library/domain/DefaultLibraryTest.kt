package dev.opendungeon.feature.library.domain

import app.cash.turbine.test
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.NameStory
import dev.opendungeon.domain.drafting.NamingError
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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

    private class Setup(val library: Library, val stories: InMemoryStories, val naming: FakeNaming, val scope: Job)

    /** Begins [stories] in order, so the last is the one played last. */
    private suspend fun TestScope.library(
        vararg stories: Story,
        naming: (InMemoryStories) -> FakeNaming = ::FakeNaming,
    ): Setup {
        val stored = InMemoryStories()
        stories.forEach { stored.begin(it, from = DraftId("draft")) }
        val names = naming(stored)
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val library = DefaultLibrary(scope, stored, names, undoWindow = WINDOW)
        return Setup(library, stored, names, scope.coroutineContext[Job]!!)
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
    fun `untitled stories are named one at a time, each asked about once`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val setup = library(
            story("a"),
            story("b"),
            salt,
            naming = { FakeNaming(it).apply { this.gate = gate } },
        )

        assertEquals(listOf(StoryId("b")), setup.naming.asked, "the one played last first, and one at a time")
        assertEquals(listOf("The Salt Key", null, null), setup.shown)
        gate.complete(Unit)

        assertEquals(listOf(StoryId("b"), StoryId("a")), setup.naming.asked)
        assertEquals(listOf("The Salt Key", "Named b", "Named a"), setup.shown)
    }

    @Test
    fun `a story that cannot be named stays untitled, and is not asked about again`() =
        runTest(UnconfinedTestDispatcher()) {
            val setup = library(story("a"), naming = { FakeNaming(it).apply { fails = true } })

            setup.stories.save(story("a").copy(guide = "Again."))

            assertEquals(listOf(StoryId("a")), setup.naming.asked)
            assertEquals(listOf<String?>(null), setup.shown)
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
        val library = library(story("a"), naming = { FakeNaming(it).apply { fails = true } }).library

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
    fun `cancelling keeps nothing, and verbs without an open title change nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val setup = library(salt)

            setup.library.saveTitle()
            setup.library.delete()
            setup.library.undoDelete()
            setup.library.typeTitle("Brine")
            setup.library.edit(StoryId("elsewhere"))
            setup.library.edit(salt.id)
            setup.library.typeTitle("Brine")
            setup.library.cancelEdit()

            assertNull(setup.library.state.value.editing)
            assertEquals(listOf("The Salt Key"), setup.shown)
            assertEquals(listOf("begin salt"), setup.stories.writes)
        }

    @Test
    fun `a deleted story leaves at once and comes back on undo, never removed`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)
        setup.library.edit(salt.id)

        setup.library.delete()

        assertNull(setup.library.state.value.editing)
        assertEquals(salt.id, setup.library.state.value.undo)
        assertEquals(listOf("Snowbound"), setup.shown)
        setup.library.edit(salt.id)
        assertNull(setup.library.state.value.editing, "a deleted story cannot be opened")

        setup.library.undoDelete()
        advanceTimeBy(WINDOW * 2)

        assertNull(setup.library.state.value.undo)
        assertEquals(listOf("Snowbound", "The Salt Key"), setup.shown)
        assertEquals(salt, setup.stories.stored(salt.id)?.copy(title = salt.title))
    }

    @Test
    fun `a deleted story is removed for good once the undo window ends`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt, snow)
        setup.library.edit(salt.id)
        setup.library.delete()

        advanceTimeBy(WINDOW - 1.seconds)
        assertEquals(salt.id, setup.library.state.value.undo)
        advanceTimeBy(2.seconds)

        assertNull(setup.library.state.value.undo)
        assertNull(setup.stories.stored(salt.id))
        assertEquals(listOf("Snowbound"), setup.shown)
        setup.library.undoDelete()
        assertEquals(listOf("Snowbound"), setup.shown, "too late to take back")
    }

    @Test
    fun `a delete storage refuses brings the story back, and says so`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt)

        setup.library.failures.test {
            setup.library.edit(salt.id)
            setup.library.delete()
            setup.stories.fail = 1
            advanceTimeBy(WINDOW + 1.seconds)

            assertEquals(LibraryFailure.Delete(StorageError.Full), awaitItem())
            assertEquals(listOf("The Salt Key"), setup.shown)
            assertNull(setup.library.state.value.undo)
        }
    }

    @Test
    fun `a second delete removes the first for good, and only the second can be taken back`() =
        runTest(UnconfinedTestDispatcher()) {
            val setup = library(salt, snow)
            setup.library.edit(salt.id)
            setup.library.delete()

            setup.library.edit(snow.id)
            setup.library.delete()
            runCurrent()

            assertNull(setup.stories.stored(salt.id))
            assertEquals(snow.id, setup.library.state.value.undo)
            setup.library.undoDelete()
            assertEquals(listOf("Snowbound"), setup.shown)
        }

    @Test
    fun `closing the library inside the undo window still removes the story`() = runTest(UnconfinedTestDispatcher()) {
        val setup = library(salt)
        setup.library.edit(salt.id)
        setup.library.delete()

        setup.scope.cancel()
        runCurrent()

        assertNull(setup.stories.stored(salt.id))
    }

    private companion object {
        val WINDOW = 5.seconds

        fun story(id: String, title: String? = null, genre: String? = null) = Story(
            id = StoryId(id),
            title = title,
            setup = StorySetup(genres = listOfNotNull(genre?.let(Phrase::Offered))),
            beats = listOf(Beat(BeatId("$id-1"), Beat.Kind.Opening, "Rain falls.")),
        )
    }
}
