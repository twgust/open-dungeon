package dev.opendungeon.feature.connect.ui

import app.cash.turbine.test
import dev.opendungeon.core.model.DataError
import dev.opendungeon.feature.connect.domain.ConnectInteractor
import dev.opendungeon.feature.connect.domain.Connection
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
class ConnectViewModelTest {

    /** Records the verbs it is called with, and lets a test push state and failures. */
    private class RecordingInteractor : ConnectInteractor {
        val calls = mutableListOf<String>()
        override val state = MutableStateFlow<Connection>(Connection.Loading)
        val failureChannel = Channel<DataError>(Channel.UNLIMITED)
        override val failures: Flow<DataError> = failureChannel.receiveAsFlow()

        override fun connect(rawKey: String) { calls += "connect($rawKey)" }
        override fun refresh() { calls += "refresh" }
        override fun disconnect() { calls += "disconnect" }
    }

    private val interactor = RecordingInteractor()
    private val factory = ConnectInteractor.Factory { _: CoroutineScope -> interactor }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each intent reaches its verb unchanged`() {
        val viewModel = ConnectViewModel(factory)

        viewModel.onIntent(ConnectIntent.Submit("key"))
        viewModel.onIntent(ConnectIntent.Refresh)
        viewModel.onIntent(ConnectIntent.Disconnect)

        assertEquals(listOf("connect(key)", "refresh", "disconnect"), interactor.calls)
    }

    @Test
    fun `the interactor's state reaches the screen unchanged`() {
        val viewModel = ConnectViewModel(factory)

        interactor.state.value = Connection.Disconnected(DataError.Network)

        assertEquals(Connection.Disconnected(DataError.Network), viewModel.state.value)
    }

    @Test
    fun `a failure becomes an effect`() = runTest {
        val viewModel = ConnectViewModel(factory)

        viewModel.effects.test {
            interactor.failureChannel.send(DataError.KeyStorage)
            assertEquals(ConnectEffect.ShowFailure(DataError.KeyStorage), awaitItem())
        }
    }

    @Test
    fun `the key never shows up in a state or intent dump`() {
        assertEquals("Submit(rawKey=██)", ConnectIntent.Submit("sk-or-v1-secret").toString())
    }
}
