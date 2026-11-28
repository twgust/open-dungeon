package dev.opendungeon.tools.continuity.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentKeyTest {

    private val key = "sk-or-v1-" + "a".repeat(64)

    @Test
    fun `the key comes from the environment`() {
        assertEquals(
            key,
            EnvironmentKey.read(keyFile = null, mapOf(EnvironmentKey.VARIABLE to "  $key\n")).getOrThrow().value,
        )
    }

    @Test
    fun `a key file wins over the environment`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("key").also { Files.writeString(it, key) }

        val read = EnvironmentKey.read(file, mapOf(EnvironmentKey.VARIABLE to "sk-or-v1-" + "b".repeat(64)))

        assertEquals(key, read.getOrThrow().value)
    }

    @Test
    fun `a missing or malformed key says how to fix it, without echoing the value`() {
        val missing = EnvironmentKey.read(keyFile = null, emptyMap()).exceptionOrNull()?.message.orEmpty()
        val malformed = EnvironmentKey.read(
            keyFile = null,
            mapOf(EnvironmentKey.VARIABLE to "sk-secret-123"),
        ).exceptionOrNull()?.message.orEmpty()

        assertTrue(EnvironmentKey.VARIABLE in missing)
        assertTrue("does not hold an OpenRouter key" in malformed)
        assertFalse("sk-secret-123" in malformed)
    }

    @Test
    fun `the store hands out its one key`() = runTest {
        val parsed = EnvironmentKey.read(null, mapOf(EnvironmentKey.VARIABLE to key)).getOrThrow()

        assertEquals(parsed, FixedApiKeyStore(parsed).apiKey.first())
    }
}
