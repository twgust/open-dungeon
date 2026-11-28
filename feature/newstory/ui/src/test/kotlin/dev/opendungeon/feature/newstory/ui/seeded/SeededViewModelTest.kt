package dev.opendungeon.feature.newstory.ui.seeded

import app.cash.turbine.test
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.Register
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.WizardFailure
import dev.opendungeon.feature.newstory.domain.WizardOutcome
import dev.opendungeon.feature.newstory.domain.seeded.Person
import dev.opendungeon.feature.newstory.domain.seeded.SeededBody
import dev.opendungeon.feature.newstory.domain.seeded.SeededControls
import dev.opendungeon.feature.newstory.domain.seeded.SeededState
import dev.opendungeon.feature.newstory.domain.seeded.SeededStep
import dev.opendungeon.feature.newstory.domain.seeded.SeededWizard
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
class SeededViewModelTest {

    /** Records the verbs it is called with, and lets a test push state and outcomes. */
    private class RecordingWizard : SeededWizard {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<SeededState?>(null)
        val outcomeChannel = Channel<WizardOutcome>(Channel.UNLIMITED)
        override val outcomes: Flow<WizardOutcome> = outcomeChannel.receiveAsFlow()

        override fun start() {
            calls += "start"
        }

        override fun pick(blank: String) {
            calls += "pick($blank)"
        }

        override fun fill(blank: String, words: String) {
            calls += "fill($blank, $words)"
        }

        override fun suggestMore(blank: String) {
            calls += "suggestMore($blank)"
        }

        override fun rollSeed() {
            calls += "rollSeed"
        }

        override fun pickShelf(shelf: Register?) {
            calls += "pickShelf($shelf)"
        }

        override fun describe(person: Person, text: String) {
            calls += "describe($person, $text)"
        }

        override fun select(choice: Choice) {
            calls += "select($choice)"
        }

        override fun submitOwn(text: String) {
            calls += "submitOwn($text)"
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

        override fun begin() {
            calls += "begin"
        }

        override fun answerLeave(answer: LeaveAnswer) {
            calls += "answerLeave($answer)"
        }
    }

    private val wizard = RecordingWizard()
    private val factory = SeededWizard.Factory { _: CoroutineScope -> wizard }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each intent reaches its verb unchanged`() {
        val viewModel = SeededViewModel(factory)
        assertEquals(emptyList(), wizard.calls, "nothing starts until the screen opens")

        viewModel.onIntent(SeededIntent.Open)
        viewModel.onIntent(SeededIntent.PickBlank("who"))
        viewModel.onIntent(SeededIntent.FillBlank("who", "boss"))
        viewModel.onIntent(SeededIntent.MoreWords("who"))
        viewModel.onIntent(SeededIntent.RollSeed)
        viewModel.onIntent(SeededIntent.PickShelf(Register.AfterDark))
        viewModel.onIntent(SeededIntent.Describe(Person.Them, "nosy"))
        viewModel.onIntent(SeededIntent.Pick(Choice.Desire(ContentLimits.Desire.Close)))
        viewModel.onIntent(SeededIntent.Own("no spiders"))
        viewModel.onIntent(SeededIntent.Next)
        viewModel.onIntent(SeededIntent.Back)
        viewModel.onIntent(SeededIntent.Stop)
        viewModel.onIntent(SeededIntent.Begin)
        viewModel.onIntent(SeededIntent.AnswerLeave(LeaveAnswer.Stay))

        assertEquals(
            listOf(
                "start",
                "pick(who)",
                "fill(who, boss)",
                "suggestMore(who)",
                "rollSeed",
                "pickShelf(AfterDark)",
                "describe(Them, nosy)",
                "select(Desire(level=Close))",
                "submitOwn(no spiders)",
                "next",
                "back",
                "stop",
                "begin",
                "answerLeave(Stay)",
            ),
            wizard.calls,
        )
    }

    @Test
    fun `the wizard's state reaches the screen unchanged`() {
        val viewModel = SeededViewModel(factory)
        assertEquals(SeededUiState.Loading, viewModel.state.value)

        val state = SeededState(
            step = SeededStep.Limits,
            progress = 0.75f,
            body = SeededBody.Limits("Caught lying by your boss.", ContentLimits()),
            controls = SeededControls(
                canAdvance = true,
                begins = true,
                canRoll = false,
                canStop = false,
                takesOwnWords = true,
            ),
            writer = null,
        )
        wizard.state.value = state

        assertEquals(SeededUiState.Ready(state), viewModel.state.value)
    }

    @Test
    fun `outcomes become effects`() = runTest {
        val viewModel = SeededViewModel(factory)
        val failure = WizardFailure.Storage(StorageError.Full)

        viewModel.effects.test {
            wizard.outcomeChannel.send(WizardOutcome.Left)
            assertEquals(SeededEffect.Leave, awaitItem())
            wizard.outcomeChannel.send(WizardOutcome.Begun(StoryId("s")))
            assertEquals(SeededEffect.Begin(StoryId("s")), awaitItem())
            wizard.outcomeChannel.send(WizardOutcome.Failed(failure))
            assertEquals(SeededEffect.ShowFailure(failure), awaitItem())
        }
    }

    @Test
    fun `asking for another model opens the models, and asks the wizard nothing`() = runTest {
        val viewModel = SeededViewModel(factory)

        viewModel.effects.test {
            viewModel.onIntent(SeededIntent.ChooseModel)
            assertEquals(SeededEffect.OpenModels, awaitItem())
        }
        assertEquals(emptyList(), wizard.calls)
    }
}
