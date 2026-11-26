package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.ReasoningSupport
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.model.TuningRanges
import dev.opendungeon.core.model.Verbosity
import dev.opendungeon.domain.openrouter.testing.aiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParameterSheetTest {

    private fun reasoning(
        mandatory: Boolean = false,
        efforts: List<String> = listOf("high", "medium", "low"),
        budget: Boolean = false,
    ) = ReasoningSupport(
        mandatory = mandatory,
        enabledByDefault = true,
        supportedEfforts = efforts.map(::ReasoningEffort),
        defaultEffort = ReasoningEffort("medium"),
        supportsBudget = budget,
    )

    @Test
    fun `every listed name is a row or a capability, in the model's order`() {
        val listed = listOf(
            "tools",
            "temperature",
            "max_tokens",
            "reasoning",
            "include_reasoning",
            "max_completion_tokens",
            "top_k",
            "response_format",
            "brand_new_knob",
            "stop",
            "seed",
            "verbosity",
            "top_p",
            "min_p",
            "top_a",
            "frequency_penalty",
            "presence_penalty",
            "repetition_penalty",
            "reasoning_effort",
            "logit_bias",
        )
        val sheet = ParameterSheet.of(aiModel("a/m", parameters = listed, reasoning = reasoning()), Tuning.Untouched)

        assertEquals(
            listOf(
                Setting.Temperature,
                Setting.MaxTokens,
                Setting.Reasoning,
                Setting.TopK,
                Setting.Stop,
                Setting.Seed,
                Setting.Verbosity,
                Setting.TopP,
                Setting.MinP,
                Setting.TopA,
                Setting.FrequencyPenalty,
                Setting.PresencePenalty,
                Setting.RepetitionPenalty,
            ),
            sheet.rows.map { it.setting },
        )
        assertEquals(listOf("tools", "response_format", "brand_new_knob", "logit_bias"), sheet.capabilities)
        assertEquals(listed.toSet(), (sheet.rows.flatMap { it.wireNames } + sheet.capabilities).toSet(), "nothing lost")
        assertEquals(listOf("max_tokens", "max_completion_tokens"), sheet.rows[1].wireNames)
        assertEquals(listOf("reasoning", "include_reasoning", "reasoning_effort"), sheet.rows[2].wireNames)
    }

    @Test
    fun `the deprecated include_reasoning alone is only a capability`() {
        val sheet = ParameterSheet.of(aiModel("a/m", parameters = listOf("include_reasoning")), Tuning.Untouched)

        assertEquals(emptyList(), sheet.rows)
        assertEquals(listOf("include_reasoning"), sheet.capabilities)
    }

    @Test
    fun `sliders start at the model's defaults, else the documented ones, and carry their ranges`() {
        val model = aiModel(
            "a/m",
            parameters = listOf("temperature", "top_p", "min_p", "repetition_penalty", "frequency_penalty"),
            defaults = DefaultParameters(temperature = 0.6, topP = null, frequencyPenalty = 9.0),
        )

        val sliders = ParameterSheet.of(model, Tuning(minP = 0.1)).rows.map { assertIs<ParameterRow.Slider>(it) }

        assertEquals(listOf(0.6, 1.0, 0.0, 1.0, 2.0), sliders.map { it.start }, "a default out of range is pulled in")
        assertEquals(
            listOf(
                TuningRanges.Temperature,
                TuningRanges.Probability,
                TuningRanges.Probability,
                TuningRanges.RepetitionPenalty,
                TuningRanges.Penalty,
            ),
            sliders.map { it.range },
        )
        assertEquals(listOf(null, null, 0.1, null, null), sliders.map { it.value })
        assertTrue(sliders[2].isSet)
        assertFalse(sliders[0].isSet)
    }

    @Test
    fun `whole numbers carry their bounds`() {
        val model = aiModel(
            "a/m",
            parameters = listOf("top_k", "max_tokens", "seed"),
            maxCompletionTokens = null,
            contextLength = 32_768,
            defaults = DefaultParameters(topK = 40),
        )

        val (topK, maxTokens, seed) = ParameterSheet.of(model, Tuning(seed = -5)).rows.map {
            assertIs<ParameterRow.Whole>(it)
        }

        assertEquals(0L, topK.min)
        assertNull(topK.max)
        assertEquals(40L, topK.modelDefault)
        assertEquals(1L, maxTokens.min)
        assertEquals(32_768L, maxTokens.max, "without an output limit the context bounds a reply")
        assertNull(seed.min)
        assertEquals(-5L, seed.value)
        assertTrue(maxTokens.accepts(32_768))
        assertFalse(maxTokens.accepts(0))
        assertFalse(maxTokens.accepts(32_769))
    }

    @Test
    fun `stops and verbosity show what is set`() {
        val sheet = ParameterSheet.of(
            aiModel("a/m", parameters = listOf("stop", "verbosity")),
            Tuning(stop = listOf("END"), verbosity = Verbosity.High),
        )

        val stops = assertIs<ParameterRow.Stops>(sheet.rows[0])
        assertEquals(listOf("END"), stops.values)
        assertEquals(4, stops.max)
        assertFalse(stops.isFull)
        val verbosity = assertIs<ParameterRow.VerbosityChoice>(sheet.rows[1])
        assertEquals(Verbosity.entries, verbosity.options)
        assertEquals(Verbosity.High, verbosity.value)
    }

    @Test
    fun `reasoning offers the model's own efforts, off unless mandatory, and a budget where it travels`() {
        val optional = ParameterSheet.of(
            aiModel("a/m", parameters = listOf("reasoning"), reasoning = reasoning(budget = true)),
            Tuning(reasoning = Reasoning.Budget(1024)),
        ).rows.single()
        val mandatory = ParameterSheet.of(
            aiModel(
                "a/m",
                parameters = listOf("reasoning_effort"),
                reasoning = reasoning(mandatory = true, budget = true),
            ),
            Tuning.Untouched,
        ).rows.single()

        assertIs<ParameterRow.ReasoningChoice>(optional)
        assertEquals(listOf("high", "medium", "low").map(::ReasoningEffort), optional.efforts)
        assertTrue(optional.canTurnOff)
        assertTrue(optional.takesBudget)
        assertEquals(true, optional.onByDefault)
        assertEquals(ReasoningEffort("medium"), optional.defaultEffort)
        assertEquals(Reasoning.Budget(1024), optional.value)
        assertIs<ParameterRow.ReasoningChoice>(mandatory)
        assertFalse(mandatory.canTurnOff)
        assertFalse(mandatory.takesBudget, "reasoning_effort has no budget")
    }

    @Test
    fun `without reasoning details, reasoning_effort takes every effort`() {
        val row = ParameterSheet.of(
            aiModel("a/m", parameters = listOf("reasoning_effort")),
            Tuning.Untouched,
        ).rows.single()

        assertIs<ParameterRow.ReasoningChoice>(row)
        assertEquals(ReasoningEffort.All, row.efforts)
        assertNull(row.onByDefault)
    }
}
