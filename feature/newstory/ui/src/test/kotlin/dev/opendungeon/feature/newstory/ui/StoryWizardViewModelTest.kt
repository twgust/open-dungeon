package dev.opendungeon.feature.newstory.ui

import app.cash.turbine.test
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Sentence
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepControls
import dev.opendungeon.feature.newstory.domain.StoryWizard
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StoryWizardViewModelTest {

    /** Records the verbs it is called with, and lets a test push state and outcomes. */
    private class RecordingWizard : StoryWizard {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<WizardState?>(null)
        val outcomeChannel = Channel<WizardOutcome>(Channel.UNLIMITED)
        override val outcomes: Flow<WizardOutcome> = outcomeChannel.receiveAsFlow()

        override fun start(draft: DraftId?) { calls += "start($draft)" }
        override fun select(choice: Choice) { calls += "select($choice)" }
        override fun submitOwn(text: String) { calls += "submitOwn($text)" }
        override fun edit(edit: DraftEdit) { calls += "edit($edit)" }
        override fun roll() { calls += "roll" }
        override fun rollLine(line: CharacterLine) { calls += "rollLine($line)" }
        override fun next() { calls += "next" }
        override fun back() { calls += "back" }
    }

    private val wizard = RecordingWizard()
    private val factory = StoryWizard.Factory { _: CoroutineScope -> wizard }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the wizard is started once, and each intent reaches its verb unchanged`() {
        val viewModel = StoryWizardViewModel(factory, DefaultWizardUiMapper())

        viewModel.onIntent(WizardIntent.Pick(Choice.Word("Gothic")))
        viewModel.onIntent(WizardIntent.Own("salt-punk"))
        viewModel.onIntent(WizardIntent.Edit(DraftEdit.FirstLine("I come in.")))
        viewModel.onIntent(WizardIntent.Roll)
        viewModel.onIntent(WizardIntent.RollLine(CharacterLine.Flaw))
        viewModel.onIntent(WizardIntent.Next)
        viewModel.onIntent(WizardIntent.Back)

        assertEquals(
            listOf(
                "start(null)",
                "select(Word(text=Gothic))",
                "submitOwn(salt-punk)",
                "edit(FirstLine(text=I come in.))",
                "roll",
                "rollLine(Flaw)",
                "next",
                "back",
            ),
            wizard.calls,
        )
    }

    @Test
    fun `the wizard's state maps through to the screen`() {
        val viewModel = StoryWizardViewModel(factory, DefaultWizardUiMapper())
        assertEquals(WizardUiState.Loading, viewModel.state.value)

        wizard.state.value = WizardState(
            draft = DraftId("d"),
            step = CreationStep.FirstLine,
            progress = 0.9f,
            lead = Sentence.Empty,
            body = StepBody.FirstLine("I come in.", maxLength = 4000),
            controls = StepControls(canGoBack = true, canAdvance = true, advancesOnPick = false, canRoll = false, takesOwnWords = false),
        )

        val step = assertIs<WizardUiState.Step>(viewModel.state.value)
        assertEquals(CreationStep.FirstLine, step.step)
        assertEquals(StepBodyUi.FirstLine("I come in.", 4000), step.body)
    }

    @Test
    fun `leaving the wizard becomes a leave effect`() = runTest {
        val viewModel = StoryWizardViewModel(factory, DefaultWizardUiMapper())

        viewModel.effects.test {
            wizard.outcomeChannel.send(WizardOutcome.Left)
            assertEquals(WizardEffect.Leave, awaitItem())
        }
    }
}
