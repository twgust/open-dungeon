package dev.opendungeon.feature.newstory.ui

import app.cash.turbine.test
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Sentence
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepControls
import dev.opendungeon.feature.newstory.domain.StoryWizard
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.WizardOutcome
import dev.opendungeon.feature.newstory.domain.WizardState
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
class StoryWizardViewModelTest {

    /** Records the verbs it is called with, and lets a test push state and outcomes. */
    private class RecordingWizard : StoryWizard {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<WizardState?>(null)
        val outcomeChannel = Channel<WizardOutcome>(Channel.UNLIMITED)
        override val outcomes: Flow<WizardOutcome> = outcomeChannel.receiveAsFlow()

        override fun start(draft: DraftId?) {
            calls += "start($draft)"
        }

        override fun select(choice: Choice) {
            calls += "select($choice)"
        }

        override fun submitOwn(text: String) {
            calls += "submitOwn($text)"
        }

        override fun edit(edit: DraftEdit) {
            calls += "edit($edit)"
        }

        override fun roll(line: CharacterLine?) {
            calls += "roll($line)"
        }

        override fun next() {
            calls += "next"
        }

        override fun back() {
            calls += "back"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun undo() {
            calls += "undo"
        }

        override fun begin() {
            calls += "begin"
        }

        override fun answerLeave(answer: LeaveAnswer) {
            calls += "answerLeave($answer)"
        }
    }

    private val wizard = RecordingWizard()
    private val factory = StoryWizard.Factory { _: CoroutineScope -> wizard }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each intent reaches its verb unchanged`() {
        val viewModel = StoryWizardViewModel(factory)
        assertEquals(emptyList(), wizard.calls, "nothing starts until the screen opens")

        viewModel.onIntent(WizardIntent.Open)
        viewModel.onIntent(WizardIntent.Pick(Choice.Word("Gothic")))
        viewModel.onIntent(WizardIntent.Own("salt-punk"))
        viewModel.onIntent(WizardIntent.Edit(DraftEdit.Opening("I come in.")))
        viewModel.onIntent(WizardIntent.Roll())
        viewModel.onIntent(WizardIntent.Roll(CharacterLine.Flaw))
        viewModel.onIntent(WizardIntent.Next)
        viewModel.onIntent(WizardIntent.Back)
        viewModel.onIntent(WizardIntent.Stop)
        viewModel.onIntent(WizardIntent.Undo)
        viewModel.onIntent(WizardIntent.Begin)
        viewModel.onIntent(WizardIntent.AnswerLeave(LeaveAnswer.Leave))
        viewModel.onIntent(WizardIntent.AnswerLeave(LeaveAnswer.Stay))

        assertEquals(
            listOf(
                "start(null)",
                "select(Word(text=Gothic))",
                "submitOwn(salt-punk)",
                "edit(Opening(text=I come in.))",
                "roll(null)",
                "roll(Flaw)",
                "next",
                "back",
                "stop",
                "undo",
                "begin",
                "answerLeave(Leave)",
                "answerLeave(Stay)",
            ),
            wizard.calls,
        )
    }

    @Test
    fun `the wizard's state reaches the screen unchanged`() {
        val viewModel = StoryWizardViewModel(factory)
        assertEquals(WizardUiState.Loading, viewModel.state.value)

        val state = WizardState(
            draft = DraftId("d"),
            step = CreationStep.Opening,
            progress = 1f,
            lead = Sentence.Empty,
            body = StepBody.Opening(
                text = "I come in.",
                writing = null,
                wand = StepBody.Opening.Wand.ContinueMyText,
                addition = null,
                failed = false,
                maxLength = 4000,
            ),
            controls = StepControls(
                canGoBack = true,
                canAdvance = true,
                begins = true,
                advancesOnPick = false,
                canRoll = false,
                takesOwnWords = false,
            ),
        )
        wizard.state.value = state

        assertEquals(WizardUiState.Ready(state), viewModel.state.value)
    }

    @Test
    fun `leaving the wizard becomes a leave effect`() = runTest {
        val viewModel = StoryWizardViewModel(factory)

        viewModel.effects.test {
            wizard.outcomeChannel.send(WizardOutcome.Left)
            assertEquals(WizardEffect.Leave, awaitItem())
        }
    }

    @Test
    fun `beginning the story becomes a begin effect carrying the draft`() = runTest {
        val viewModel = StoryWizardViewModel(factory)

        viewModel.effects.test {
            wizard.outcomeChannel.send(WizardOutcome.Begun(DraftId("d")))
            assertEquals(WizardEffect.Begin(DraftId("d")), awaitItem())
        }
    }

    @Test
    fun `asking for another model opens the models, and asks the wizard nothing`() = runTest {
        val viewModel = StoryWizardViewModel(factory)

        viewModel.effects.test {
            viewModel.onIntent(WizardIntent.ChooseModel)
            assertEquals(WizardEffect.OpenModels, awaitItem())
        }
        assertEquals(emptyList(), wizard.calls)
    }

    @Test
    fun `a failure becomes an effect carrying it`() = runTest {
        val viewModel = StoryWizardViewModel(factory)
        val failure = WizardFailure.Storage(StorageError.Full)

        viewModel.effects.test {
            wizard.outcomeChannel.send(WizardOutcome.Failed(failure))
            assertEquals(WizardEffect.ShowFailure(failure), awaitItem())
        }
    }
}
