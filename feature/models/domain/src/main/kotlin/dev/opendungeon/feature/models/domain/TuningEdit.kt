package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.model.Verbosity
import java.math.BigDecimal
import java.math.RoundingMode

/** One change the player made in the editor. */
public sealed interface TuningEdit {
    /** Moves a slider; the value is kept inside the parameter's range, in hundredths. */
    public data class Slide(val setting: Setting, val value: Double) : TuningEdit

    /** Types a whole number; one outside the row's bounds is not taken. */
    public data class Type(val setting: Setting, val value: Long) : TuningEdit

    public data class AddStop(val sequence: String) : TuningEdit

    public data class RemoveStop(val index: Int) : TuningEdit

    public data class PickVerbosity(val value: Verbosity) : TuningEdit

    public data class PickReasoning(val value: Reasoning) : TuningEdit

    /** Leaves [setting] to the provider again. */
    public data class Reset(val setting: Setting) : TuningEdit
}

/**
 * [tuning] with [edit] applied, checked against [row], the row the edit is for. An edit the row does not
 * allow, such as a number out of bounds, a fifth stop or an effort the model does not take, changes
 * nothing.
 */
internal fun applyEdit(tuning: Tuning, edit: TuningEdit, row: ParameterRow): Tuning = when (edit) {
    is TuningEdit.Slide -> (row as? ParameterRow.Slider)?.let { tuning.slid(edit, it) }
    is TuningEdit.Type -> (row as? ParameterRow.Whole)?.let { tuning.typed(edit, it) }
    is TuningEdit.AddStop -> (row as? ParameterRow.Stops)?.let { tuning.withStop(edit.sequence, it) }
    is TuningEdit.RemoveStop -> tuning.withoutStop(edit.index)
    is TuningEdit.PickVerbosity -> (row as? ParameterRow.VerbosityChoice)
        ?.takeIf { edit.value in it.options }
        ?.let { tuning.copy(verbosity = edit.value) }
    is TuningEdit.PickReasoning -> (row as? ParameterRow.ReasoningChoice)
        ?.takeIf { it.allows(edit.value) }
        ?.let { tuning.copy(reasoning = edit.value) }
    is TuningEdit.Reset -> tuning.without(edit.setting)
} ?: tuning

private fun Tuning.slid(edit: TuningEdit.Slide, row: ParameterRow.Slider): Tuning? =
    withNumber(edit.setting, edit.value.toStep().coerceIn(row.range))

private fun Tuning.typed(edit: TuningEdit.Type, row: ParameterRow.Whole): Tuning? =
    if (row.accepts(edit.value)) withWhole(edit.setting, edit.value) else null

private fun Tuning.withStop(sequence: String, row: ParameterRow.Stops): Tuning? {
    val fits = !row.isFull && sequence.isNotEmpty() && sequence !in row.values
    return if (fits) copy(stop = stop + sequence) else null
}

private fun Tuning.withoutStop(index: Int): Tuning? =
    if (index in stop.indices) copy(stop = stop.filterIndexed { i, _ -> i != index }) else null

/** The setting an edit is for, so the caller can find its row. */
internal val TuningEdit.target: Setting
    get() = when (this) {
        is TuningEdit.Slide -> this.setting
        is TuningEdit.Type -> this.setting
        is TuningEdit.AddStop, is TuningEdit.RemoveStop -> Setting.Stop
        is TuningEdit.PickVerbosity -> Setting.Verbosity
        is TuningEdit.PickReasoning -> Setting.Reasoning
        is TuningEdit.Reset -> this.setting
    }

/** Sliders move in hundredths: finer than any provider distinguishes, and readable on screen. */
public const val SLIDER_DECIMALS: Int = 2

private fun Double.toStep(): Double =
    BigDecimal.valueOf(this).setScale(SLIDER_DECIMALS, RoundingMode.HALF_UP).toDouble()

private fun ParameterRow.ReasoningChoice.allows(reasoning: Reasoning): Boolean = when (reasoning) {
    Reasoning.Off -> canTurnOff
    is Reasoning.On -> reasoning.effort == null || reasoning.effort in efforts
    is Reasoning.Budget -> takesBudget
}

private fun Tuning.withNumber(setting: Setting, value: Double): Tuning? = when (setting) {
    Setting.Temperature -> copy(temperature = value)
    Setting.TopP -> copy(topP = value)
    Setting.MinP -> copy(minP = value)
    Setting.TopA -> copy(topA = value)
    Setting.FrequencyPenalty -> copy(frequencyPenalty = value)
    Setting.PresencePenalty -> copy(presencePenalty = value)
    Setting.RepetitionPenalty -> copy(repetitionPenalty = value)
    else -> null
}

private fun Tuning.withWhole(setting: Setting, value: Long): Tuning? = when (setting) {
    Setting.TopK -> value.toIntOrNull()?.let { copy(topK = it) }
    Setting.MaxTokens -> value.toIntOrNull()?.let { copy(maxTokens = it) }
    Setting.Seed -> copy(seed = value)
    else -> null
}

private fun Long.toIntOrNull(): Int? = takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

private fun Tuning.without(setting: Setting): Tuning = when (setting) {
    Setting.Temperature -> copy(temperature = null)
    Setting.TopP -> copy(topP = null)
    Setting.TopK -> copy(topK = null)
    Setting.MinP -> copy(minP = null)
    Setting.TopA -> copy(topA = null)
    Setting.FrequencyPenalty -> copy(frequencyPenalty = null)
    Setting.PresencePenalty -> copy(presencePenalty = null)
    Setting.RepetitionPenalty -> copy(repetitionPenalty = null)
    Setting.MaxTokens -> copy(maxTokens = null)
    Setting.Seed -> copy(seed = null)
    Setting.Stop -> copy(stop = emptyList())
    Setting.Verbosity -> copy(verbosity = null)
    Setting.Reasoning -> copy(reasoning = null)
}
