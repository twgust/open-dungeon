package dev.opendungeon.feature.newstory.ui.choice

import app.cash.turbine.test
import dev.opendungeon.feature.newstory.domain.CreationPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NewStoryChoiceViewModelTest {

    private class FixedPaths(private val resumes: Boolean) : CreationPaths {
        override suspend fun customResumes(): Boolean = resumes
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening tells whether the custom path resumes a draft`() {
        val viewModel = NewStoryChoiceViewModel(FixedPaths(resumes = true))
        assertEquals(ChoiceUiState(customResumes = false), viewModel.state.value, "both paths show at once")

        viewModel.onIntent(ChoiceIntent.Open)

        assertEquals(ChoiceUiState(customResumes = true), viewModel.state.value)
    }

    @Test
    fun `each choice becomes its navigation`() = runTest {
        val viewModel = NewStoryChoiceViewModel(FixedPaths(resumes = false))

        viewModel.effects.test {
            viewModel.onIntent(ChoiceIntent.Seeded)
            assertEquals(ChoiceEffect.OpenSeeded, awaitItem())
            viewModel.onIntent(ChoiceIntent.Custom)
            assertEquals(ChoiceEffect.OpenCustom, awaitItem())
            viewModel.onIntent(ChoiceIntent.Back)
            assertEquals(ChoiceEffect.Leave, awaitItem())
        }
    }
}
