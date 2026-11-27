package dev.opendungeon.feature.library.ui

import app.cash.turbine.test
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.library.domain.Library
import dev.opendungeon.feature.library.domain.LibraryFailure
import dev.opendungeon.feature.library.domain.LibraryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    /** Records the verbs it is called with, and lets a test push state and failures. */
    private class RecordingLibrary : Library {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow(LibraryState())
        val failureChannel = Channel<LibraryFailure>(Channel.UNLIMITED)
        override val failures: Flow<LibraryFailure> = failureChannel.receiveAsFlow()

        override fun edit(story: StoryId) = record("edit(${story.value})")

        override fun typeTitle(text: String) = record("typeTitle($text)")

        override fun saveTitle() = record("saveTitle")

        override fun cancelEdit() = record("cancelEdit")

        override fun delete(story: StoryId) = record("delete(${story.value})")

        override fun undoDelete() = record("undoDelete")

        private fun record(call: String) {
            calls += call
        }
    }

    private val library = RecordingLibrary()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LibraryViewModel(Library.Factory { _: CoroutineScope -> library })

    @Test
    fun `intents reach the library as verbs, one each`() {
        val viewModel = viewModel()

        listOf(
            LibraryIntent.Edit(StoryId("salt")),
            LibraryIntent.TypeTitle("Brine"),
            LibraryIntent.SaveTitle,
            LibraryIntent.CancelEdit,
            LibraryIntent.Delete(StoryId("snow")),
            LibraryIntent.Undo,
        ).forEach(viewModel::onIntent)

        assertEquals(
            listOf("edit(salt)", "typeTitle(Brine)", "saveTitle", "cancelEdit", "delete(snow)", "undoDelete"),
            library.calls,
        )
    }

    @Test
    fun `the library's state is the screen's state`() {
        val viewModel = viewModel()
        val loaded = LibraryState(loading = false, undo = StoryId("salt"))

        library.state.value = loaded

        assertEquals(LibraryUiState(loaded), viewModel.state.value)
    }

    @Test
    fun `opening, starting and failures go out as effects, and touch no verb`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(LibraryIntent.Open(StoryId("salt")))
            assertEquals(LibraryEffect.OpenStory(StoryId("salt")), awaitItem())
            viewModel.onIntent(LibraryIntent.NewStory)
            assertEquals(LibraryEffect.StartStory, awaitItem())
            library.failureChannel.send(LibraryFailure.Delete(StorageError.Full))
            assertEquals(LibraryEffect.ShowFailure(LibraryFailure.Delete(StorageError.Full)), awaitItem())
        }
        assertEquals(emptyList(), library.calls)
    }
}
