package dev.opendungeon.data.generation

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.model.Verbosity
import dev.opendungeon.domain.generation.Usage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoogMappersTest {

    @Test
    fun `capabilities follow supported parameters`() {
        val toolCaller = aiModel(
            parameters = setOf("tools", "tool_choice", "structured_outputs", "temperature", "reasoning_effort"),
        ).toLLModel()
        val plain = aiModel(parameters = setOf("max_tokens")).toLLModel()

        assertTrue(toolCaller.supports(LLMCapability.Tools))
        assertTrue(toolCaller.supports(LLMCapability.ToolChoice))
        assertTrue(toolCaller.supports(LLMCapability.Schema.JSON.Standard))
        assertTrue(toolCaller.supports(LLMCapability.Temperature))
        assertTrue(toolCaller.supports(LLMCapability.Thinking))
        assertFalse(plain.supports(LLMCapability.Schema.JSON.Standard))
        assertFalse(plain.supports(LLMCapability.Tools))
        assertFalse(plain.supports(LLMCapability.Thinking))
        assertTrue(plain.supports(LLMCapability.Completion))
        assertEquals(131_072L, plain.contextLength)
        assertEquals(8_192L, plain.maxOutputTokens)
    }

    @Test
    fun `unsupported values are dropped, supported ones kept`() {
        val tuning = Tuning(
            temperature = 1.1,
            topP = 0.9,
            topK = 50,
            minP = 0.05,
            topA = 0.2,
            repetitionPenalty = 1.1,
            seed = 3,
            stop = listOf("THE END"),
            verbosity = Verbosity.Low,
        )

        val params = tuning.toOpenRouterParams(
            model = aiModel(parameters = setOf("temperature", "min_p", "top_a", "stop")),
            sessionId = null,
            requireParameters = false,
        )

        assertEquals(1.1, params.temperature)
        assertEquals(0.05, params.minP)
        assertEquals(0.2, params.topA)
        assertEquals(listOf("THE END"), params.stop)
        assertNull(params.topP)
        assertNull(params.topK)
        assertNull(params.repetitionPenalty)
        assertNull(params.additionalProperties, "seed and verbosity are unsupported and there is no session")
    }

    @Test
    fun `a tuned request only goes to providers that honour it`() {
        val tuned = Tuning(temperature = 0.4).toOpenRouterParams(
            model = aiModel(parameters = setOf("temperature")),
            sessionId = null,
            requireParameters = false,
        )
        val untouched = Tuning.Untouched.toOpenRouterParams(
            model = aiModel(parameters = setOf("temperature")),
            sessionId = "story-9",
            requireParameters = false,
        )
        val unsupported = Tuning(seed = 3).toOpenRouterParams(
            model = aiModel(parameters = setOf("temperature")),
            sessionId = null,
            requireParameters = false,
        )

        assertEquals(true, tuned.provider?.requireParameters)
        assertNull(untouched.provider, "a session is routing, not tuning")
        assertNull(unsupported.provider, "nothing tuned is sent")
    }

    @Test
    fun `structured output requires parameters even when nothing is tuned`() {
        val params = Tuning.Untouched.toOpenRouterParams(aiModel(), sessionId = null, requireParameters = true)

        assertEquals(true, params.provider?.requireParameters)
    }

    @Test
    fun `session, seed and verbosity travel as extra body fields`() {
        val params = Tuning(seed = 11, verbosity = Verbosity.ExtraHigh).toOpenRouterParams(
            model = aiModel(parameters = setOf("seed", "verbosity")),
            sessionId = "story-9",
            requireParameters = false,
        )

        val extra = requireNotNull(params.additionalProperties)
        assertEquals("story-9", extra.getValue("session_id").jsonPrimitive.content)
        assertEquals("11", extra.getValue("seed").jsonPrimitive.content)
        assertEquals("xhigh", extra.getValue("verbosity").jsonPrimitive.content)
    }

    @Test
    fun `the token limit goes under max_completion_tokens when the model lists it`() {
        val both = Tuning(maxTokens = 600).toOpenRouterParams(
            model = aiModel(parameters = setOf("max_tokens", "max_completion_tokens")),
            sessionId = null,
            requireParameters = false,
        )
        val legacy = Tuning(maxTokens = 600).toOpenRouterParams(
            model = aiModel(parameters = setOf("max_tokens")),
            sessionId = null,
            requireParameters = false,
        )

        assertNull(both.maxTokens)
        assertEquals("600", both.additionalProperties?.getValue("max_completion_tokens")?.jsonPrimitive?.content)
        assertEquals(600, legacy.maxTokens)
        assertNull(legacy.additionalProperties)
    }

    @Test
    fun `top-k of zero is off, the same as leaving it out`() {
        val params = Tuning(topK = 0).toOpenRouterParams(
            model = aiModel(parameters = setOf("top_k")),
            sessionId = null,
            requireParameters = false,
        )

        assertNull(params.topK)
        assertNull(params.provider)
    }

    @Test
    fun `reasoning goes as the reasoning object when the model lists it`() {
        val model = aiModel(parameters = setOf("reasoning", "reasoning_effort"))

        fun sent(reasoning: Reasoning) = Tuning(reasoning = reasoning)
            .toOpenRouterParams(model, sessionId = null, requireParameters = false)
            .additionalProperties
            ?.getValue("reasoning")
            ?.jsonObject

        assertEquals(buildJsonObject { put("effort", "high") }, sent(Reasoning.On(ReasoningEffort("high"))))
        assertEquals(buildJsonObject { put("enabled", true) }, sent(Reasoning.On(effort = null)))
        assertEquals(buildJsonObject { put("enabled", false) }, sent(Reasoning.Off))
        assertEquals(buildJsonObject { put("max_tokens", 2048) }, sent(Reasoning.Budget(2048)))
    }

    @Test
    fun `reasoning falls back to reasoning_effort, which has no budget`() {
        val model = aiModel(parameters = setOf("reasoning_effort"))

        fun sent(reasoning: Reasoning) = Tuning(reasoning = reasoning)
            .toOpenRouterParams(model, sessionId = null, requireParameters = false)
            .additionalProperties
            ?.get("reasoning_effort")

        assertEquals(JsonPrimitive("low"), sent(Reasoning.On(ReasoningEffort("low"))))
        assertEquals(JsonPrimitive("none"), sent(Reasoning.Off))
        assertNull(sent(Reasoning.On(effort = null)))
        assertNull(sent(Reasoning.Budget(512)))
    }

    @Test
    fun `usage carries cost and reasoning tokens exactly`() {
        val meta = ResponseMetaInfo.create(
            KoogClock.System,
            totalTokensCount = 52,
            inputTokensCount = 40,
            outputTokensCount = 12,
            metadata = buildJsonObject {
                put(OpenRouterChatClient.MetaKeys.COST, JsonPrimitive(BigDecimal("0.000123456789")))
                put(OpenRouterChatClient.MetaKeys.REASONING_TOKENS, 5)
            },
        )

        assertEquals(
            Usage(promptTokens = 40, completionTokens = 12, reasoningTokens = 5, cost = BigDecimal("0.000123456789")),
            meta.toUsage(),
        )
        assertNull(ResponseMetaInfo.create(KoogClock.System).toUsage(), "no counts, no usage")
    }
}
