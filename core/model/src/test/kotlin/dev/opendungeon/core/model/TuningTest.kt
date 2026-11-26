package dev.opendungeon.core.model

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TuningTest {

    @Test
    fun `rejects values outside the documented ranges`() {
        assertFailsWith<IllegalArgumentException> { Tuning(temperature = 2.5) }
        assertFailsWith<IllegalArgumentException> { Tuning(topP = 1.2) }
        assertFailsWith<IllegalArgumentException> { Tuning(minP = -0.1) }
        assertFailsWith<IllegalArgumentException> { Tuning(topA = 1.5) }
        assertFailsWith<IllegalArgumentException> { Tuning(frequencyPenalty = -2.1) }
        assertFailsWith<IllegalArgumentException> { Tuning(repetitionPenalty = -0.1) }
        assertFailsWith<IllegalArgumentException> { Tuning(topK = -1) }
        assertFailsWith<IllegalArgumentException> { Tuning(maxTokens = 0) }
        assertFailsWith<IllegalArgumentException> { Tuning(stop = listOf("a", "b", "c", "d", "e")) }
        assertFailsWith<IllegalArgumentException> { Tuning(stop = listOf("")) }
    }

    @Test
    fun `accepts the edges of each range, top-k off included`() {
        Tuning(temperature = 0.0, topP = 1.0, topK = 0, frequencyPenalty = -2.0, presencePenalty = 2.0, maxTokens = 1)
    }

    @Test
    fun `untouched means nothing is set`() {
        assertTrue(Tuning().isUntouched)
        assertFalse(Tuning(seed = 1).isUntouched)
    }
}
