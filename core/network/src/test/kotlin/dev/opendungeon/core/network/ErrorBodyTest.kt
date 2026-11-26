package dev.opendungeon.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorBodyTest {

    @Test
    fun `reads the documented envelope`() {
        val error = decodeOpenRouterError("""{"error":{"code":402,"message":"Insufficient credits","metadata":{"x":1}}}""")

        assertEquals(402, error?.code)
        assertEquals("Insufficient credits", error?.message)
    }

    @Test
    fun `anything else is no envelope`() {
        assertNull(decodeOpenRouterError("<html>Bad gateway</html>"))
        assertNull(decodeOpenRouterError("""{"detail":"nope"}"""))
        assertNull(decodeOpenRouterError(""))
    }
}
