package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.ReasoningSupport
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.model.Verbosity
import dev.opendungeon.domain.openrouter.testing.aiModel
import kotlin.test.Test
import kotlin.test.assertEquals

class TuningEditTest {

    private val model = aiModel(
        "a/m",
        parameters = listOf("temperature", "top_k", "max_tokens", "seed", "stop", "verbosity", "reasoning"),
        maxCompletionTokens = 4_096,
        reasoning = ReasoningSupport(
            mandatory = true,
            enabledByDefault = true,
            supportedEfforts = listOf(ReasoningEffort("high"), ReasoningEffort("low")),
            defaultEffort = null,
            supportsBudget = false,
        ),
    )

    private fun Tuning.after(edit: TuningEdit): Tuning {
        val row = ParameterSheet.of(model, this).rows.first { it.setting == edit.target }
        return applyEdit(this, edit, row)
    }

    @Test
    fun `a slider keeps inside its range, in hundredths`() {
        assertEquals(Tuning(temperature = 0.7), Tuning().after(TuningEdit.Slide(Setting.Temperature, 0.70321)))
        assertEquals(Tuning(temperature = 0.71), Tuning().after(TuningEdit.Slide(Setting.Temperature, 0.705)))
        assertEquals(Tuning(temperature = 2.0), Tuning().after(TuningEdit.Slide(Setting.Temperature, 3.5)))
    }

    @Test
    fun `a whole number outside its bounds is not taken`() {
        assertEquals(Tuning(maxTokens = 4_096), Tuning().after(TuningEdit.Type(Setting.MaxTokens, 4_096)))
        assertEquals(Tuning(), Tuning().after(TuningEdit.Type(Setting.MaxTokens, 4_097)))
        assertEquals(Tuning(), Tuning().after(TuningEdit.Type(Setting.MaxTokens, 0)))
        assertEquals(Tuning(topK = 0), Tuning().after(TuningEdit.Type(Setting.TopK, 0)))
        assertEquals(Tuning(), Tuning().after(TuningEdit.Type(Setting.TopK, Long.MAX_VALUE)), "beyond what top_k holds")
        assertEquals(Tuning(seed = Long.MIN_VALUE), Tuning().after(TuningEdit.Type(Setting.Seed, Long.MIN_VALUE)))
    }

    @Test
    fun `an edit of the wrong kind for its row changes nothing`() {
        assertEquals(Tuning(), Tuning().after(TuningEdit.Slide(Setting.TopK, 0.5)))
        assertEquals(Tuning(), Tuning().after(TuningEdit.Type(Setting.Temperature, 1)))
    }

    @Test
    fun `stops are added up to four, never empty or twice, and removed by position`() {
        val three = Tuning(stop = listOf("a", "b", "c"))

        val four = three.after(TuningEdit.AddStop("d"))
        assertEquals(listOf("a", "b", "c", "d"), four.stop)
        assertEquals(four, four.after(TuningEdit.AddStop("e")), "a fifth is not taken")
        assertEquals(three, three.after(TuningEdit.AddStop("")))
        assertEquals(three, three.after(TuningEdit.AddStop("b")))
        assertEquals(listOf("a", "c"), three.after(TuningEdit.RemoveStop(1)).stop)
        assertEquals(three, three.after(TuningEdit.RemoveStop(7)))
    }

    @Test
    fun `reasoning takes only what the model allows`() {
        assertEquals(
            Tuning(reasoning = Reasoning.On(ReasoningEffort("low"))),
            Tuning().after(TuningEdit.PickReasoning(Reasoning.On(ReasoningEffort("low")))),
        )
        assertEquals(Tuning(), Tuning().after(TuningEdit.PickReasoning(Reasoning.On(ReasoningEffort("minimal")))))
        assertEquals(Tuning(), Tuning().after(TuningEdit.PickReasoning(Reasoning.Off)), "reasoning is mandatory")
        assertEquals(Tuning(), Tuning().after(TuningEdit.PickReasoning(Reasoning.Budget(512))), "no budget")
    }

    @Test
    fun `reset leaves a setting to the provider`() {
        val tuned = Tuning(temperature = 0.2, stop = listOf("x"), verbosity = Verbosity.Low, seed = 4)

        assertEquals(tuned.copy(temperature = null), tuned.after(TuningEdit.Reset(Setting.Temperature)))
        assertEquals(tuned.copy(stop = emptyList()), tuned.after(TuningEdit.Reset(Setting.Stop)))
        assertEquals(tuned.copy(verbosity = null), tuned.after(TuningEdit.Reset(Setting.Verbosity)))
        assertEquals(tuned.copy(seed = null), tuned.after(TuningEdit.Reset(Setting.Seed)))
        assertEquals(Tuning(verbosity = Verbosity.Max), Tuning().after(TuningEdit.PickVerbosity(Verbosity.Max)))
    }
}
