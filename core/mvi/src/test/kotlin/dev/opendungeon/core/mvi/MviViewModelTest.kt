package dev.opendungeon.core.mvi

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MviViewModelTest {

    private class CounterViewModel : MviViewModel<Int, CounterViewModel.Intent, String>(initialState = 0) {
        sealed interface Intent {
            data object Increment : Intent
            data object Announce : Intent
        }

        override fun onIntent(intent: Intent) = when (intent) {
            Intent.Increment -> reduce { this + 1 }
            Intent.Announce -> sendEffect("announced")
        }
    }

    @Test
    fun `intents reduce state and emit one-off effects`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterViewModel.Intent.Increment)
        viewModel.onIntent(CounterViewModel.Intent.Increment)
        viewModel.onIntent(CounterViewModel.Intent.Announce)

        assertEquals(2, viewModel.state.value)
        viewModel.effects.test { assertEquals("announced", awaitItem()) }
    }

    @Test
    fun `an effect sent before anyone collects is delivered once, to the first collector`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterViewModel.Intent.Announce)

        viewModel.effects.test { assertEquals("announced", awaitItem()) }
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `reductions apply in order, each to the state the last one left`() = runTest {
        val viewModel = CounterViewModel()

        repeat(100) { viewModel.onIntent(CounterViewModel.Intent.Increment) }

        assertEquals(100, viewModel.state.value)
    }
}
