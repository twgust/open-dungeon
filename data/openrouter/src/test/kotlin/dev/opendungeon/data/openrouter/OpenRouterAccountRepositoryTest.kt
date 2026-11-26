package dev.opendungeon.data.openrouter

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.network.NetworkError
import dev.opendungeon.core.network.dto.CurrentKeyDto
import dev.opendungeon.core.network.dto.ErrorDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OpenRouterAccountRepositoryTest {

    @Test
    fun `stores the key only after OpenRouter accepts it`() = runTest {
        val store = FakeApiKeyStore()
        val repository = DefaultOpenRouterAccountRepository(
            api = FakeOpenRouterApi(key = { Outcome.Success(CurrentKeyDto(label = "story key", limit = 5.0)) }),
            store = store,
        )

        val result = repository.connect("  $VALID_KEY_TEXT ")

        assertEquals("story key", assertIs<Outcome.Success<KeyInfo>>(result).value.label)
        assertEquals(VALID_KEY, store.apiKey.value)
    }

    @Test
    fun `a rejected key is not stored`() = runTest {
        val store = FakeApiKeyStore()
        val repository = DefaultOpenRouterAccountRepository(
            api = FakeOpenRouterApi(key = { Outcome.Failure(NetworkError.Http(401, ErrorDto(401, "User not found."))) }),
            store = store,
        )

        val result = repository.connect(VALID_KEY_TEXT)

        assertEquals(Outcome.Failure(DataError.Unauthorized("User not found.")), result)
        assertNull(store.apiKey.value)
    }

    @Test
    fun `malformed input never reaches the network`() = runTest {
        val repository = DefaultOpenRouterAccountRepository(api = FakeOpenRouterApi(), store = FakeApiKeyStore())

        assertEquals(Outcome.Failure(DataError.MalformedApiKey), repository.connect("sk-ant-123"))
    }

    @Test
    fun `an accepted key the device cannot store is a failure, not a connection`() = runTest {
        val store = FakeApiKeyStore(broken = true)
        val repository = DefaultOpenRouterAccountRepository(
            api = FakeOpenRouterApi(key = { Outcome.Success(CurrentKeyDto(label = "story key")) }),
            store = store,
        )

        assertEquals(Outcome.Failure(DataError.KeyStorage), repository.connect(VALID_KEY_TEXT))
        assertNull(store.apiKey.value)
    }

    @Test
    fun `disconnecting removes the key, and says so when it cannot`() = runTest {
        val working = FakeApiKeyStore(initial = VALID_KEY)
        assertEquals(Outcome.Success(Unit), DefaultOpenRouterAccountRepository(FakeOpenRouterApi(), working).disconnect())
        assertNull(working.apiKey.value)

        val broken = FakeApiKeyStore(initial = VALID_KEY, broken = true)
        assertEquals(Outcome.Failure(DataError.KeyStorage), DefaultOpenRouterAccountRepository(FakeOpenRouterApi(), broken).disconnect())
        assertEquals(VALID_KEY, broken.apiKey.value)
    }
}
