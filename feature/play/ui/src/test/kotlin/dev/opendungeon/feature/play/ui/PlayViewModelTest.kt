package dev.opendungeon.feature.play.ui

import app.cash.turbine.test
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.play.domain.LeaveAnswer
import dev.opendungeon.feature.play.domain.Play
import dev.opendungeon.feature.play.domain.PlayFailure
import dev.opendungeon.feature.play.domain.PlayOutcome
import dev.opendungeon.feature.play.domain.PlaySession
import dev.opendungeon.feature.play.domain.Refusal
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
class PlayViewModelTest {

    /** Records the verbs it is called with, and lets a test push state and outcomes. */
    private class RecordingSession : PlaySession {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<Play>(Play.Opening)
        val outcomeChannel = Channel<PlayOutcome>(Channel.UNLIMITED)
        override val outcomes: Flow<PlayOutcome> = outcomeChannel.receiveAsFlow()

        override fun start(story: StoryId) = record("start(${story.value})")

        override fun typeTurn(text: String) = record("typeTurn($text)")

        override fun advance() = record("advance")

        override fun retry() = record("retry")

        override fun openGuide() = record("openGuide")

        override fun openReason() = record("openReason")

        override fun editBeat(beat: BeatId) = record("editBeat(${beat.value})")

        override fun typeNote(text: String) = record("typeNote($text)")

        override fun submit() = record("submit")

        override fun cancel() = record("cancel")

        override fun removeGuide() = record("removeGuide")

        override fun stop() = record("stop")

        override fun undo() = record("undo")

        override fun tryAgain() = record("tryAgain")

        override fun dismissFailure() = record("dismissFailure")

        override fun back() = record("back")

        override fun answerLeave(answer: LeaveAnswer) = record("answerLeave($answer)")

        private fun record(call: String) {
            calls += call
        }
    }

    private val session = RecordingSession()
    private val factory = PlaySession.Factory { _: CoroutineScope -> session }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each intent reaches its verb unchanged`() {
        val viewModel = PlayViewModel(factory)
        assertEquals(emptyList(), session.calls, "nothing opens until the screen does")

        listOf(
            PlayIntent.Open(StoryId("salt")),
            PlayIntent.TypeTurn("I wait."),
            PlayIntent.Advance,
            PlayIntent.Retry,
            PlayIntent.OpenGuide,
            PlayIntent.OpenReason,
            PlayIntent.EditBeat(BeatId("b2")),
            PlayIntent.TypeNote("Too neat."),
            PlayIntent.Submit,
            PlayIntent.Cancel,
            PlayIntent.RemoveGuide,
            PlayIntent.Stop,
            PlayIntent.Undo,
            PlayIntent.TryAgain,
            PlayIntent.DismissFailure,
            PlayIntent.Back,
            PlayIntent.AnswerLeave(LeaveAnswer.Stay),
            PlayIntent.AnswerLeave(LeaveAnswer.Leave),
        ).forEach(viewModel::onIntent)

        assertEquals(
            listOf(
                "start(salt)",
                "typeTurn(I wait.)",
                "advance",
                "retry",
                "openGuide",
                "openReason",
                "editBeat(b2)",
                "typeNote(Too neat.)",
                "submit",
                "cancel",
                "removeGuide",
                "stop",
                "undo",
                "tryAgain",
                "dismissFailure",
                "back",
                "answerLeave(Stay)",
                "answerLeave(Leave)",
            ),
            session.calls,
        )
    }

    @Test
    fun `the session's state reaches the screen unchanged`() {
        val viewModel = PlayViewModel(factory)
        assertEquals(PlayUiState(Play.Opening), viewModel.state.value)

        session.state.value = Play.Missing

        assertEquals(PlayUiState(Play.Missing), viewModel.state.value)
    }

    @Test
    fun `outcomes become effects carrying them`() = runTest {
        val viewModel = PlayViewModel(factory)
        val failure = PlayFailure.Storage(StorageError.Full)

        viewModel.effects.test {
            session.outcomeChannel.send(PlayOutcome.Left)
            assertEquals(PlayEffect.Leave, awaitItem())
            session.outcomeChannel.send(PlayOutcome.Failed(failure))
            assertEquals(PlayEffect.ShowFailure(failure), awaitItem())
            session.outcomeChannel.send(PlayOutcome.Refused(Refusal.StopFirst))
            assertEquals(PlayEffect.ShowRefusal(Refusal.StopFirst), awaitItem())
        }
    }

    @Test
    fun `asking for another model opens the models, and asks the session nothing`() = runTest {
        val viewModel = PlayViewModel(factory)

        viewModel.effects.test {
            viewModel.onIntent(PlayIntent.ChooseModel)
            assertEquals(PlayEffect.OpenModels, awaitItem())
        }
        assertEquals(emptyList(), session.calls)
    }
}
