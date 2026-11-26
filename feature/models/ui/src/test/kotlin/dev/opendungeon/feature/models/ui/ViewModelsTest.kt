package dev.opendungeon.feature.models.ui

import app.cash.turbine.test
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.feature.models.domain.ModelPicker
import dev.opendungeon.feature.models.domain.ModelTuner
import dev.opendungeon.feature.models.domain.PickerEvent
import dev.opendungeon.feature.models.domain.PickerState
import dev.opendungeon.feature.models.domain.Setting
import dev.opendungeon.feature.models.domain.TryEvent
import dev.opendungeon.feature.models.domain.TryRun
import dev.opendungeon.feature.models.domain.TryState
import dev.opendungeon.feature.models.domain.TunerState
import dev.opendungeon.feature.models.domain.TuningEdit
import dev.opendungeon.feature.models.ui.picker.ModelPickerViewModel
import dev.opendungeon.feature.models.ui.picker.PickerEffect
import dev.opendungeon.feature.models.ui.picker.PickerIntent
import dev.opendungeon.feature.models.ui.trial.TryEffect
import dev.opendungeon.feature.models.ui.trial.TryIntent
import dev.opendungeon.feature.models.ui.trial.TryViewModel
import dev.opendungeon.feature.models.ui.tuner.ModelTunerViewModel
import dev.opendungeon.feature.models.ui.tuner.TunerEffect
import dev.opendungeon.feature.models.ui.tuner.TunerIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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

/** The ViewModels only translate: each intent reaches one verb unchanged, and state and events pass through. */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private val calls = mutableListOf<String>()

    private inner class RecordingPicker : ModelPicker {
        override val state = MutableStateFlow(PickerState())
        val eventChannel = Channel<PickerEvent>(Channel.UNLIMITED)
        override val events = eventChannel.receiveAsFlow()

        override fun search(query: String) {
            calls += "search($query)"
        }

        override fun sortBy(sort: ModelSort?) {
            calls += "sortBy($sort)"
        }

        override fun toggleInput(modality: String) {
            calls += "toggleInput($modality)"
        }

        override fun toggleFreeOnly() {
            calls += "toggleFreeOnly"
        }

        override fun toggleTools() {
            calls += "toggleTools"
        }

        override fun toggleContext(tokens: Int) {
            calls += "toggleContext($tokens)"
        }

        override fun refresh() {
            calls += "refresh"
        }

        override fun choose(id: ModelId) {
            calls += "choose(${id.value})"
        }
    }

    private inner class RecordingTuner : ModelTuner {
        override val state = MutableStateFlow<TunerState>(TunerState.Loading)
        val failureChannel = Channel<DataError>(Channel.UNLIMITED)
        override val failures = failureChannel.receiveAsFlow()

        override fun edit(edit: TuningEdit) {
            calls += "edit($edit)"
        }

        override fun resetAll() {
            calls += "resetAll"
        }

        override fun retryProviders() {
            calls += "retryProviders"
        }
    }

    private inner class RecordingRun : TryRun {
        override val state = MutableStateFlow(TryState())
        val eventChannel = Channel<TryEvent>(Channel.UNLIMITED)
        override val events = eventChannel.receiveAsFlow()

        override fun send(prompt: String) {
            calls += "send($prompt)"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun leave() {
            calls += "leave"
        }

        override fun confirmLeave() {
            calls += "confirmLeave"
        }

        override fun stay() {
            calls += "stay"
        }
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the picker's intents reach their verbs, and its events become effects`() = runTest {
        val picker = RecordingPicker()
        val viewModel = ModelPickerViewModel { picker }

        listOf(
            PickerIntent.Search("hermes"),
            PickerIntent.SortBy(ModelSort.Newest),
            PickerIntent.ToggleInput("image"),
            PickerIntent.ToggleFreeOnly,
            PickerIntent.ToggleTools,
            PickerIntent.ToggleContext(128_000),
            PickerIntent.Refresh,
            PickerIntent.Choose(ModelId("a/b")),
        ).forEach(viewModel::onIntent)
        picker.state.value = PickerState(total = 3)

        assertEquals(
            listOf(
                "search(hermes)",
                "sortBy(Newest)",
                "toggleInput(image)",
                "toggleFreeOnly",
                "toggleTools",
                "toggleContext(128000)",
                "refresh",
                "choose(a/b)",
            ),
            calls,
        )
        assertEquals(PickerState(total = 3), viewModel.state.value)
        viewModel.effects.test {
            picker.eventChannel.send(PickerEvent.Chosen(ModelId("a/b")))
            assertEquals(PickerEffect.OpenChosen, awaitItem())
            picker.eventChannel.send(PickerEvent.Failed(DataError.Storage))
            assertEquals(PickerEffect.ShowFailure(DataError.Storage), awaitItem())
        }
    }

    @Test
    fun `the tuner's intents reach their verbs, and its failures become effects`() = runTest {
        val tuner = RecordingTuner()
        val viewModel = ModelTunerViewModel { tuner }

        viewModel.onIntent(TunerIntent.Edit(TuningEdit.Reset(Setting.Seed)))
        viewModel.onIntent(TunerIntent.ResetAll)
        viewModel.onIntent(TunerIntent.RetryProviders)
        tuner.state.value = TunerState.NoModel

        assertEquals(listOf("edit(Reset(setting=Seed))", "resetAll", "retryProviders"), calls)
        assertEquals(TunerState.NoModel, viewModel.state.value)
        viewModel.effects.test {
            tuner.failureChannel.send(DataError.Storage)
            assertEquals(TunerEffect.ShowFailure(DataError.Storage), awaitItem())
        }
    }

    @Test
    fun `the run's intents reach their verbs, and leaving becomes an effect`() = runTest {
        val run = RecordingRun()
        val viewModel = TryViewModel { run }

        listOf(TryIntent.Send("Go"), TryIntent.Stop, TryIntent.Leave, TryIntent.ConfirmLeave, TryIntent.Stay)
            .forEach(viewModel::onIntent)
        run.state.value = TryState(reply = "Smoke")

        assertEquals(listOf("send(Go)", "stop", "leave", "confirmLeave", "stay"), calls)
        assertEquals(TryState(reply = "Smoke"), viewModel.state.value)
        viewModel.effects.test {
            run.eventChannel.send(TryEvent.Leave)
            assertEquals(TryEffect.Leave, awaitItem())
        }
    }
}
