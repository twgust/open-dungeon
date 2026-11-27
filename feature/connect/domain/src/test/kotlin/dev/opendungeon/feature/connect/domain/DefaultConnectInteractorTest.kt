package dev.opendungeon.feature.connect.domain

import app.cash.turbine.test
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.OpenRouterAccountRepository
import dev.opendungeon.domain.openrouter.testing.FETCHED_AT
import dev.opendungeon.domain.openrouter.testing.FakeModelCatalog
import dev.opendungeon.domain.openrouter.testing.FakeModelSetup
import dev.opendungeon.domain.openrouter.testing.aiModel
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.testing.InMemoryStories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConnectInteractorTest {

    private val keyInfo =
        KeyInfo(label = "story key", isFreeTier = false, usage = 0.0, limit = null, limitRemaining = null)

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

    private val model = aiModel("nous/hermes-4-405b")
    private val storedCatalog = Catalog(listOf(model), FETCHED_AT)
    private val loaded = Connection.Connected(Remote.Loaded(keyInfo), ModelChoice.None)
    private val opening = Beat(BeatId("b1"), Beat.Kind.Opening, "Rain falls.")

    private fun TestScope.interactor(
        account: FakeAccount,
        catalog: FakeModelCatalog = FakeModelCatalog(),
        setup: FakeModelSetup = FakeModelSetup(),
        stories: InMemoryStories = InMemoryStories(),
    ) = DefaultConnectInteractor(backgroundScope, account, catalog, setup, stories)

    @Test
    fun `home is the library once a story exists, and not after the last is removed`() =
        runTest(UnconfinedTestDispatcher()) {
            val stories = InMemoryStories()
            val interactor =
                interactor(FakeAccount(connected = true), FakeModelCatalog(storedCatalog), stories = stories)
            assertEquals(loaded, interactor.state.value)

            stories.begin(Story(StoryId("salt"), StorySetup(), listOf(opening)), from = DraftId("draft"))
            assertEquals(loaded.copy(showsLibrary = true), interactor.state.value)

            stories.remove(StoryId("salt"))
            assertEquals(loaded, interactor.state.value)
        }

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
    fun `checking starts at once, and a second submit while checking is ignored`() =
        runTest(UnconfinedTestDispatcher()) {
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
    fun `without a stored catalog one is fetched, and a failure leaves the key details in place`() =
        runTest(UnconfinedTestDispatcher()) {
            val catalog = FakeModelCatalog().apply { refreshResult = Outcome.Failure(DataError.Network) }
            val interactor = interactor(FakeAccount(connected = true), catalog)

            assertEquals(
                Connection.Connected(Remote.Loaded(keyInfo), ModelChoice.Unavailable(DataError.Network)),
                interactor.state.value,
            )
            assertEquals(1, catalog.refreshes)
        }

    @Test
    fun `a stored catalog is not fetched again on opening`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(storedCatalog)

        interactor(FakeAccount(connected = true), catalog)

        assertEquals(0, catalog.refreshes)
    }

    @Test
    fun `the chosen model shows, and follows a choice made elsewhere`() = runTest(UnconfinedTestDispatcher()) {
        val setup = FakeModelSetup()
        val interactor = interactor(FakeAccount(connected = true), FakeModelCatalog(storedCatalog), setup)
        assertEquals(loaded, interactor.state.value)

        setup.choose(model.id)

        assertEquals(Connection.Connected(Remote.Loaded(keyInfo), ModelChoice.Chosen(model)), interactor.state.value)
    }

    @Test
    fun `a chosen model the catalog no longer offers reads as no choice`() = runTest(UnconfinedTestDispatcher()) {
        val interactor = interactor(
            FakeAccount(connected = true),
            FakeModelCatalog(storedCatalog),
            FakeModelSetup(chosen = ModelId("gone/model")),
        )

        assertEquals(loaded, interactor.state.value)
    }

    @Test
    fun `a refresh fetches the catalog and replaces a slower load still on its way`() =
        runTest(UnconfinedTestDispatcher()) {
            val catalog = FakeModelCatalog()
            val first = catalog.hold()
            val second = catalog.hold()
            val interactor = interactor(FakeAccount(connected = true), catalog)

            interactor.refresh()
            assertEquals(
                Connection.Connected(Remote.Loaded(keyInfo), ModelChoice.Loading),
                interactor.state.value,
                "a refresh shows at once",
            )
            second.complete(Outcome.Success(listOf(model)))
            first.complete(Outcome.Failure(DataError.Network))

            assertEquals(loaded, interactor.state.value, "the older answer never lands")
        }

    @Test
    fun `a failed refresh over a stored catalog is reported and the catalog stays`() =
        runTest(UnconfinedTestDispatcher()) {
            val catalog = FakeModelCatalog(storedCatalog).apply { refreshResult = Outcome.Failure(DataError.Network) }
            val interactor = interactor(FakeAccount(connected = true), catalog)

            interactor.failures.test {
                interactor.refresh()

                assertEquals(DataError.Network, awaitItem())
            }
            assertEquals(loaded, interactor.state.value)
        }

    @Test
    fun `refresh means nothing without a key`() = runTest(UnconfinedTestDispatcher()) {
        val interactor = interactor(FakeAccount())

        interactor.refresh()

        assertEquals(Connection.Disconnected(), interactor.state.value)
    }

    @Test
    fun `disconnecting shows at once and forgets the key's catalog`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(storedCatalog)
        val interactor = interactor(FakeAccount(connected = true), catalog)

        interactor.disconnect()

        assertEquals(Connection.Disconnected(), interactor.state.value)
        assertEquals(1, catalog.clears)
        assertNull(catalog.catalog.first())
    }

    @Test
    fun `a catalog the device cannot forget is reported`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(storedCatalog).apply { clearResult = Outcome.Failure(DataError.Storage) }
        val interactor = interactor(FakeAccount(connected = true), catalog)

        interactor.failures.test {
            interactor.disconnect()

            assertEquals(DataError.Storage, awaitItem())
        }
        assertEquals(Connection.Disconnected(), interactor.state.value)
    }

    @Test
    fun `a key the device cannot remove comes back, and the failure is reported`() =
        runTest(UnconfinedTestDispatcher()) {
            val account = FakeAccount(connected = true).apply {
                disconnectResult = Outcome.Failure(DataError.KeyStorage)
            }
            val catalog = FakeModelCatalog(storedCatalog)
            val interactor = interactor(account, catalog)

            interactor.failures.test {
                interactor.disconnect()

                assertEquals(DataError.KeyStorage, awaitItem())
            }
            assertEquals(loaded, interactor.state.value)
            assertEquals(0, catalog.clears)
        }

    @Test
    fun `a key removed elsewhere returns to key entry`() = runTest(UnconfinedTestDispatcher()) {
        val account = FakeAccount(connected = true)
        val interactor = interactor(account)

        account.isConnected.value = false

        assertIs<Connection.Disconnected>(interactor.state.value)
    }
}
