package dev.opendungeon.tools.continuity.model

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.Tuning

/** A model as the harness calls it: OpenRouter's own description of it, and what each request sends. */
internal data class ModelChoice(val model: AiModel, val tuning: Tuning) {

    companion object {
        /**
         * The cheap, competent model runs default to, checked against OpenRouter's live catalog on
         * 2026-11-27: DeepSeek V4.1 Flash, $0.051 in and $0.567 out per million tokens, with structured
         * outputs and reasoning that can be turned off. `z-ai/glm-5.3-flash` is the GLM alternative.
         */
        const val DEFAULT_MODEL: String = "deepseek/deepseek-v4.1-flash"

        /** Enough for a long beat, and for a model that cannot stop reasoning to reason and still answer. */
        private const val ANSWER_TOKENS = 1_500

        /**
         * How the model under test is called. Sampling is left to the provider, as the app leaves it until
         * the player tunes it, so the numbers describe the model as players meet it.
         */
        fun subject(model: AiModel, reasoning: ReasoningChoice, temperature: Double?): ModelChoice = ModelChoice(
            model,
            Tuning(temperature = temperature, maxTokens = ANSWER_TOKENS, reasoning = reasoning.on(model)),
        )

        /** How the judge is called: as deterministic as the model allows, and without thinking where it can stop. */
        fun judge(model: AiModel): ModelChoice =
            ModelChoice(model, Tuning(temperature = 0.0, reasoning = ReasoningChoice.Least.on(model)))
    }
}

/** How much a reasoning model thinks before it answers. */
internal enum class ReasoningChoice(val wireName: String) {
    /** The model's own default. */
    Default("default"),

    /** Off where the model allows it, else its lowest effort. Cheapest and fastest. */
    Least("least"),

    /** The model's highest effort. */
    Most("most"),

    ;

    /** The setting to send to [model]; `null` sends none, which also covers models that do not reason. */
    fun on(model: AiModel): Reasoning? {
        val support = model.reasoning ?: return null
        return when (this) {
            Default -> null
            Least -> if (!support.mandatory) Reasoning.Off else support.supportedEfforts.lastOrNull().toOn()
            Most -> support.supportedEfforts.firstOrNull().toOn()
        }
    }

    private fun ReasoningEffort?.toOn(): Reasoning = Reasoning.On(effort = this)

    companion object {
        fun fromWireName(name: String): ReasoningChoice? = entries.firstOrNull { it.wireName == name }
    }
}
