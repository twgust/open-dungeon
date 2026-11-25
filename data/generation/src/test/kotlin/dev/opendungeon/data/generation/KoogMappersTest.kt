package dev.opendungeon.data.generation

import ai.koog.prompt.llm.LLMCapability
import dev.opendungeon.domain.generation.Sampling
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoogMappersTest {

    @Test
    fun `capabilities follow supported parameters`() {
        val toolCaller = aiModel(parameters = setOf("tools", "tool_choice", "structured_outputs", "temperature")).toLLModel()
        val plain = aiModel(parameters = setOf("max_tokens")).toLLModel()

        assertTrue(toolCaller.supports(LLMCapability.Tools))
        assertTrue(toolCaller.supports(LLMCapability.ToolChoice))
        assertTrue(toolCaller.supports(LLMCapability.Schema.JSON.Standard))
        assertTrue(toolCaller.supports(LLMCapability.Temperature))
        assertFalse(plain.supports(LLMCapability.Schema.JSON.Standard))
        assertFalse(plain.supports(LLMCapability.Tools))
        assertTrue(plain.supports(LLMCapability.Completion))
        assertEquals(131_072L, plain.contextLength)
        assertEquals(8_192L, plain.maxOutputTokens)
    }

    @Test
    fun `unsupported sampling values are dropped, supported ones kept`() {
        val sampling = Sampling(temperature = 1.1, topP = 0.9, topK = 50, minP = 0.05, repetitionPenalty = 1.1, seed = 3)

        val params = sampling.toOpenRouterParams(
            model = aiModel(parameters = setOf("temperature", "min_p")),
            sessionId = null,
            requireParameters = false,
        )

        assertEquals(1.1, params.temperature)
        assertEquals(0.05, params.minP)
        assertNull(params.topP)
        assertNull(params.topK)
        assertNull(params.repetitionPenalty)
        assertNull(params.additionalProperties, "seed is unsupported and there is no session")
        assertNull(params.provider)
    }

    @Test
    fun `session and seed travel as extra body fields`() {
        val params = Sampling(seed = 11).toOpenRouterParams(
            model = aiModel(parameters = setOf("seed")),
            sessionId = "story-9",
            requireParameters = true,
        )

        val extra = requireNotNull(params.additionalProperties)
        assertEquals("story-9", extra.getValue("session_id").jsonPrimitive.content)
        assertEquals("11", extra.getValue("seed").jsonPrimitive.content)
        assertEquals(true, params.provider?.requireParameters)
    }
}
