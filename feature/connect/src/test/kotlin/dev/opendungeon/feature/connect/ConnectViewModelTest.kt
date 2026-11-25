package dev.opendungeon.feature.connect

import app.cash.turbine.test
import dev.opendungeon.core.data.ModelCatalogRepository
import dev.opendungeon.core.data.OpenRouterAccountRepository
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.feature.connect.ConnectState.Phase
import dev.opendungeon.feature.connect.ConnectState.Remote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class ConnectViewModelTest {

    private val keyInfo = KeyInfo(label = "story key", isFreeTier = false, usage = 0.0, limit = null, limitRemaining = null)

    private class FakeAccount(private val connectResult: Outcome<KeyInfo, DataError>, private val keyInfo: KeyInfo) :
        OpenRouterAccountRepository {
        override val isConnected = MutableStateFlow(false)
        override suspend fun connect(rawKey: String) = connectResult.also { if (it is Outcome.Success) isConnected.value = true }
        override suspend fun currentKey(): Outcome<KeyInfo, DataError> = Outcome.Success(keyInfo)
        override suspend fun disconnect() { isConnected.value = false }
    }

    private val noModels = object : ModelCatalogRepository {
        override suspend fun fetchModels(): Outcome<List<AiModel>, DataError> = Outcome.Success(emptyList())
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a rejected key returns to key entry with the error`() = runTest {
        val viewModel = ConnectViewModel(FakeAccount(Outcome.Failure(DataError.Unauthorized("no")), keyInfo), noModels)

        viewModel.onIntent(ConnectIntent.Submit("sk-or-v1-whatever"))

        assertEquals(Phase.Disconnected(DataError.Unauthorized("no")), viewModel.state.value.phase)
    }

    @Test
    fun `an accepted key loads key details and the catalog, and disconnect resets`() = runTest {
        val viewModel = ConnectViewModel(FakeAccount(Outcome.Success(keyInfo), keyInfo), noModels)

        viewModel.state.test {
            assertIs<Phase.Disconnected>(awaitItem().phase)

            viewModel.onIntent(ConnectIntent.Submit("key"))

            val connected = expectMostRecentItem()
            val phase = assertIs<Phase.Connected>(connected.phase)
            assertEquals(Remote.Loaded(keyInfo), phase.key)
            assertEquals(0, assertIs<Remote.Loaded<List<AiModel>>>(phase.models).value.size)

            viewModel.onIntent(ConnectIntent.Disconnect)
            assertEquals(Phase.Disconnected(), expectMostRecentItem().phase)
        }
    }
}
