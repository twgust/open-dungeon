package dev.opendungeon.domain.generation

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RequestsTest {

    @Test
    fun `sampling rejects values no provider accepts`() {
        assertFailsWith<IllegalArgumentException> { Sampling(temperature = 2.5) }
        assertFailsWith<IllegalArgumentException> { Sampling(topP = 1.2) }
        assertFailsWith<IllegalArgumentException> { Sampling(topK = 0) }
        assertFailsWith<IllegalArgumentException> { Sampling(maxTokens = 0) }
        assertFailsWith<IllegalArgumentException> { Sampling(repetitionPenalty = -0.1) }
    }
}
