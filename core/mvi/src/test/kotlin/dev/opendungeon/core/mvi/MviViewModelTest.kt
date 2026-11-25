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
            Intent.Announce -> sendEffect("count=$currentState")
        }
    }

    @Test
    fun `intents reduce state and emit one-off effects`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterViewModel.Intent.Increment)
        viewModel.onIntent(CounterViewModel.Intent.Increment)
        viewModel.onIntent(CounterViewModel.Intent.Announce)

        assertEquals(2, viewModel.state.value)
        viewModel.effects.test { assertEquals("count=2", awaitItem()) }
    }
}
