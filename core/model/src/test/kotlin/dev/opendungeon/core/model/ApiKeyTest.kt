package dev.opendungeon.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApiKeyTest {
    private val valid = "sk-or-v1-" + "0123456789abcdef".repeat(4)

    @Test
    fun `accepts a documented key and trims whitespace`() {
        assertEquals(valid, assertNotNull(ApiKey.parse("  $valid\n")).value)
    }

    @Test
    fun `rejects keys from other providers and malformed input`() {
        assertNull(ApiKey.parse("sk-proj-abc"))
        assertNull(ApiKey.parse(valid.uppercase()))
        assertNull(ApiKey.parse(valid.dropLast(1)))
        assertNull(ApiKey.parse(""))
    }

    @Test
    fun `toString never contains the secret`() {
        assertFalse(valid.drop(12) in assertNotNull(ApiKey.parse(valid)).toString())
    }

    @Test
    fun `unknown parameters are preserved instead of failing`() {
        assertEquals(ModelParameter.Known.Temperature, ModelParameter.fromWireName("temperature"))
        assertEquals(ModelParameter.Unknown("future_param"), ModelParameter.fromWireName("future_param"))
    }
}
