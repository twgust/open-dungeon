package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelParameter.Known
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.model.TuningRanges
import dev.opendungeon.core.model.Verbosity

/** A parameter the player can set, one per row of the editor whatever names the model lists it under. */
public enum class Setting {
    Temperature,
    TopP,
    TopK,
    MinP,
    TopA,
    FrequencyPenalty,
    PresencePenalty,
    RepetitionPenalty,
    MaxTokens,
    Seed,
    Stop,
    Verbosity,
    Reasoning,
}

/**
 * The editor for one model, generated from its own `supported_parameters`: a row for every parameter the
 * player can set, in the model's order, and [capabilities] for every other name it lists, so together they
 * account for the whole list.
 */
public data class ParameterSheet(val rows: List<ParameterRow>, val capabilities: List<String>) {

    public companion object {
        /** [model]'s sheet, with values from [tuning]. */
        public fun of(model: AiModel, tuning: Tuning): ParameterSheet {
            val listed = model.supportedParameters
            val bySetting = linkedMapOf<Setting, MutableList<String>>()
            val capabilities = mutableListOf<String>()
            for (parameter in listed) {
                when (val setting = settingOf(parameter, listed)) {
                    null -> capabilities += parameter.wireName
                    else -> bySetting.getOrPut(setting, ::mutableListOf) += parameter.wireName
                }
            }
            val rows = bySetting.map { (setting, names) -> row(setting, names, model, tuning) }
            return ParameterSheet(rows, capabilities)
        }

        private fun row(setting: Setting, names: List<String>, model: AiModel, tuning: Tuning): ParameterRow {
            SliderSpecs[setting]?.let { spec -> return spec.row(setting, names, model.defaultParameters, tuning) }
            return when (setting) {
                Setting.TopK -> ParameterRow.Whole(
                    setting,
                    names,
                    min = 0,
                    max = null,
                    value = tuning.topK?.toLong(),
                    modelDefault = model.defaultParameters.topK?.toLong(),
                )
                Setting.MaxTokens -> ParameterRow.Whole(
                    setting,
                    names,
                    min = 1,
                    max = (model.maxCompletionTokens ?: model.contextLength)?.toLong(),
                    value = tuning.maxTokens?.toLong(),
                    modelDefault = null,
                )
                Setting.Seed -> ParameterRow.Whole(
                    setting,
                    names,
                    min = null,
                    max = null,
                    value = tuning.seed,
                    modelDefault = null,
                )
                Setting.Stop -> ParameterRow.Stops(names, values = tuning.stop, max = TuningRanges.MAX_STOP_SEQUENCES)
                Setting.Verbosity -> ParameterRow.VerbosityChoice(
                    names,
                    options = Verbosity.entries,
                    value = tuning.verbosity,
                )
                else -> reasoningRow(names, model, tuning.reasoning)
            }
        }

        private fun reasoningRow(names: List<String>, model: AiModel, value: Reasoning?): ParameterRow.ReasoningChoice {
            val support = model.reasoning
            val listed = model.supportedParameters
            // Without the catalog's reasoning details, a model that takes reasoning_effort takes any effort.
            val efforts = support?.supportedEfforts
                ?: if (Known.ReasoningEffort in listed) ReasoningEffort.All else emptyList()
            return ParameterRow.ReasoningChoice(
                wireNames = names,
                efforts = efforts,
                canTurnOff = support?.mandatory != true,
                // A budget only travels in the reasoning object.
                takesBudget = support?.supportsBudget == true && Known.Reasoning in listed,
                onByDefault = support?.let { it.mandatory || it.enabledByDefault },
                defaultEffort = support?.defaultEffort,
                value = value,
            )
        }

        /**
         * Which row a listed parameter belongs to, or `null` for one the app sets per request and for names
         * it does not know. `include_reasoning` is a deprecated alias: it joins the reasoning row when the
         * model lists a current name too, and is otherwise only a capability.
         */
        private fun settingOf(parameter: ModelParameter, listed: Set<ModelParameter>): Setting? = when (parameter) {
            Known.IncludeReasoning ->
                Setting.Reasoning.takeIf { Known.Reasoning in listed || Known.ReasoningEffort in listed }
            else -> SettingByParameter[parameter]
        }

        private val SettingByParameter: Map<ModelParameter, Setting> = mapOf(
            Known.Temperature to Setting.Temperature,
            Known.TopP to Setting.TopP,
            Known.TopK to Setting.TopK,
            Known.MinP to Setting.MinP,
            Known.TopA to Setting.TopA,
            Known.FrequencyPenalty to Setting.FrequencyPenalty,
            Known.PresencePenalty to Setting.PresencePenalty,
            Known.RepetitionPenalty to Setting.RepetitionPenalty,
            Known.MaxTokens to Setting.MaxTokens,
            Known.MaxCompletionTokens to Setting.MaxTokens,
            Known.Seed to Setting.Seed,
            Known.Stop to Setting.Stop,
            Known.Verbosity to Setting.Verbosity,
            Known.Reasoning to Setting.Reasoning,
            Known.ReasoningEffort to Setting.Reasoning,
        )

        /** Each slider's documented range and conventional default, and where its values live. */
        private val SliderSpecs: Map<Setting, SliderSpec> = mapOf(
            Setting.Temperature to
                SliderSpec(TuningRanges.Temperature, 1.0, Tuning::temperature, DefaultParameters::temperature),
            Setting.TopP to SliderSpec(TuningRanges.Probability, 1.0, Tuning::topP, DefaultParameters::topP),
            Setting.MinP to SliderSpec(TuningRanges.Probability, 0.0, Tuning::minP) { null },
            Setting.TopA to SliderSpec(TuningRanges.Probability, 0.0, Tuning::topA) { null },
            Setting.FrequencyPenalty to
                SliderSpec(TuningRanges.Penalty, 0.0, Tuning::frequencyPenalty, DefaultParameters::frequencyPenalty),
            Setting.PresencePenalty to
                SliderSpec(TuningRanges.Penalty, 0.0, Tuning::presencePenalty, DefaultParameters::presencePenalty),
            Setting.RepetitionPenalty to SliderSpec(
                TuningRanges.RepetitionPenalty,
                1.0,
                Tuning::repetitionPenalty,
                DefaultParameters::repetitionPenalty,
            ),
        )
    }
}

/** A slider's range, its documented conventional default, its value in a tuning, and the model's own default. */
private class SliderSpec(
    val range: ClosedFloatingPointRange<Double>,
    val conventional: Double,
    val value: (Tuning) -> Double?,
    val modelDefault: (DefaultParameters) -> Double?,
) {
    fun row(setting: Setting, names: List<String>, defaults: DefaultParameters, tuning: Tuning) = ParameterRow.Slider(
        setting = setting,
        wireNames = names,
        range = range,
        value = value(tuning),
        start = (modelDefault(defaults) ?: conventional).coerceIn(range),
    )
}

/** One row of a [ParameterSheet]. A `null` value leaves the parameter to the provider. */
public sealed interface ParameterRow {
    public val setting: Setting

    /** The names the model lists this row under, as OpenRouter spells them. */
    public val wireNames: List<String>

    /** True when the player set a value, so the request carries it. */
    public val isSet: Boolean

    /** @property start where the slider sits while unset: the model's stated default, else the documented one. */
    public data class Slider(
        override val setting: Setting,
        override val wireNames: List<String>,
        val range: ClosedFloatingPointRange<Double>,
        val value: Double?,
        val start: Double,
    ) : ParameterRow {
        override val isSet: Boolean get() = value != null
    }

    /** A whole number between [min] and [max]; `null` bounds are open. */
    public data class Whole(
        override val setting: Setting,
        override val wireNames: List<String>,
        val min: Long?,
        val max: Long?,
        val value: Long?,
        val modelDefault: Long?,
    ) : ParameterRow {
        override val isSet: Boolean get() = value != null

        public fun accepts(candidate: Long): Boolean =
            (min == null || candidate >= min) && (max == null || candidate <= max)
    }

    public data class Stops(override val wireNames: List<String>, val values: List<String>, val max: Int) :
        ParameterRow {
        override val setting: Setting get() = Setting.Stop
        override val isSet: Boolean get() = values.isNotEmpty()
        val isFull: Boolean get() = values.size >= max
    }

    public data class VerbosityChoice(
        override val wireNames: List<String>,
        val options: List<Verbosity>,
        val value: Verbosity?,
    ) : ParameterRow {
        override val setting: Setting get() = Setting.Verbosity
        override val isSet: Boolean get() = value != null
    }

    /**
     * @property efforts the efforts this model accepts, highest first; empty when it takes none.
     * @property canTurnOff false for models whose reasoning is mandatory.
     * @property onByDefault whether the model reasons when left alone; `null` when the catalog does not say.
     */
    public data class ReasoningChoice(
        override val wireNames: List<String>,
        val efforts: List<ReasoningEffort>,
        val canTurnOff: Boolean,
        val takesBudget: Boolean,
        val onByDefault: Boolean?,
        val defaultEffort: ReasoningEffort?,
        val value: Reasoning?,
    ) : ParameterRow {
        override val setting: Setting get() = Setting.Reasoning
        override val isSet: Boolean get() = value != null

        public companion object {
            /** Where a budget starts when the player picks one; they set the number from there. */
            public const val STARTING_BUDGET: Int = 2_048
        }
    }
}
