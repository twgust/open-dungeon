package dev.opendungeon.feature.connect.domain

import app.cash.turbine.test
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.OpenRouterAccountRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConnectInteractorTest {

    private val keyInfo = KeyInfo(label = "story key", isFreeTier = false, usage = 0.0, limit = null, limitRemaining = null)

    /** An account whose answers a test can hold back with [connectAnswer] or change between calls. */
    private inner class FakeAccount(connected: Boolean = false) : OpenRouterAccountRepository {
        override val isConnected = MutableStateFlow(connected)
        var connectAnswer: CompletableDeferred<Outcome<KeyInfo, DataError>>? = null
        var connectResult: Outcome<KeyInfo, DataError> = Outcome.Success(keyInfo)
        var disconnectResult: Outcome<Unit, DataError> = Outcome.Success(Unit)
        var connects = 0

        override suspend fun connect(rawKey: String): Outcome<KeyInfo, DataError> {
            connects++
            val result = connectAnswer?.await() ?: connectResult
            if (result is Outcome.Success) isConnected.value = true
            return result
        }

        override suspend fun currentKey(): Outcome<KeyInfo, DataError> = Outcome.Success(keyInfo)

        override suspend fun disconnect(): Outcome<Unit, DataError> =
            disconnectResult.also { if (it is Outcome.Success) isConnected.value = false }
    }

    /** Answers each fetch with the next deferred, so a test decides when and in which order loads finish. */
    private class HeldCatalog : ModelCatalogRepository {
        val answers = ArrayDeque<CompletableDeferred<Outcome<List<AiModel>, DataError>>>()
        var autoAnswer: Outcome<List<AiModel>, DataError>? = Outcome.Success(emptyList())

        fun hold(): CompletableDeferred<Outcome<List<AiModel>, DataError>> = CompletableDeferred<Outcome<List<AiModel>, DataError>>().also(answers::addLast)

        override suspend fun fetchModels(): Outcome<List<AiModel>, DataError> =
            answers.removeFirstOrNull()?.await() ?: requireNotNull(autoAnswer)
    }

    private val loaded = Connection.Connected(Remote.Loaded(keyInfo), Remote.Loaded(emptyList()))

    private fun TestScope.interactor(account: FakeAccount, catalog: ModelCatalogRepository = HeldCatalog()) =
        DefaultConnectInteractor(backgroundScope, account, catalog)

    @Test
    fun `a rejected key returns to key entry with the reason`() = runTest(UnconfinedTestDispatcher()) {
        val account = FakeAccount().apply { connectResult = Outcome.Failure(DataError.Unauthorized("no")) }
        val interactor = interactor(account)

        interactor.connect("sk-or-v1-whatever")

        assertEquals(Connection.Disconnected(DataError.Unauthorized("no")), interactor.state.value)
    }

    @Test
    fun `an accepted key loads its details and the catalog`() = runTest(UnconfinedTestDispatcher()) {
        val interactor = interactor(FakeAccount())

        interactor.connect("key")

        assertEquals(loaded, interactor.state.value)
    }

    @Test
    fun `checking starts at once, and a second submit while checking is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val account = FakeAccount().apply { connectAnswer = CompletableDeferred() }
        val interactor = interactor(account)

        interactor.connect("key")
        assertEquals(Connection.Validating, interactor.state.value)
        interactor.connect("key")

        assertEquals(1, account.connects)
        account.connectAnswer?.complete(Outcome.Success(keyInfo))
        assertEquals(loaded, interactor.state.value)
    }

    @Test
    fun `a catalog that fails leaves the key details in place`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = HeldCatalog().apply { autoAnswer = Outcome.Failure(DataError.Network) }
        val interactor = interactor(FakeAccount(connected = true), catalog)

        assertEquals(Connection.Connected(Remote.Loaded(keyInfo), Remote.Failed(DataError.Network)), interactor.state.value)
    }

    @Test
    fun `a refresh replaces a slower load still on its way`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = HeldCatalog()
        val first = catalog.hold()
        val second = catalog.hold()
        val interactor = interactor(FakeAccount(connected = true), catalog)

        interactor.refresh()
        assertEquals(Connection.Connected(Remote.Loading, Remote.Loading), interactor.state.value, "a refresh shows at once")
        second.complete(Outcome.Success(emptyList()))
        first.complete(Outcome.Failure(DataError.Network))

        assertEquals(loaded, interactor.state.value, "the older answer never lands")
    }

    @Test
    fun `refresh means nothing without a key`() = runTest(UnconfinedTestDispatcher()) {
        val interactor = interactor(FakeAccount())

        interactor.refresh()

        assertEquals(Connection.Disconnected(), interactor.state.value)
    }

    @Test
    fun `disconnecting shows at once`() = runTest(UnconfinedTestDispatcher()) {
        val interactor = interactor(FakeAccount(connected = true))

        interactor.disconnect()

        assertEquals(Connection.Disconnected(), interactor.state.value)
    }

    @Test
    fun `a key the device cannot remove comes back, and the failure is reported`() = runTest(UnconfinedTestDispatcher()) {
        val account = FakeAccount(connected = true).apply { disconnectResult = Outcome.Failure(DataError.KeyStorage) }
        val interactor = interactor(account)

        interactor.failures.test {
            interactor.disconnect()

            assertEquals(DataError.KeyStorage, awaitItem())
        }
        assertEquals(loaded, interactor.state.value)
    }

    @Test
    fun `a key removed elsewhere returns to key entry`() = runTest(UnconfinedTestDispatcher()) {
        val account = FakeAccount(connected = true)
        val interactor = interactor(account)

        account.isConnected.value = false

        assertIs<Connection.Disconnected>(interactor.state.value)
    }
}
