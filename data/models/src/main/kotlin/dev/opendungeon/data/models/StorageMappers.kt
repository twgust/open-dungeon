package dev.opendungeon.data.models

import dev.opendungeon.core.database.model.CatalogModelEntity
import dev.opendungeon.core.database.model.DefaultColumns
import dev.opendungeon.core.database.model.PricingColumns
import dev.opendungeon.core.database.model.ReasoningColumns
import dev.opendungeon.core.database.model.TuningColumns
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.Reasoning
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.ReasoningSupport
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.core.model.Verbosity
import java.math.BigDecimal

// Storage names are written out here rather than taken from enum names, so renaming a Kotlin constant
// never changes what is stored.

private const val PRICE_DYNAMIC = "dynamic"
private const val PRICE_UNKNOWN = "unknown"
private const val PRICE_FREE = "free"
private const val PRICE_METERED = "metered"
private const val REASONING_OFF = "off"
private const val REASONING_ON = "on"
private const val REASONING_BUDGET = "budget"

internal fun AiModel.toEntity(position: Int) = CatalogModelEntity(
    id = id.value,
    position = position,
    name = name,
    description = description,
    contextLength = contextLength,
    maxCompletionTokens = maxCompletionTokens,
    pricing = pricing.toColumns(),
    inputModalities = inputModalities.toList(),
    outputModalities = outputModalities.toList(),
    supportedParameters = supportedParameters.map(ModelParameter::wireName),
    defaults = with(defaultParameters) {
        DefaultColumns(temperature, topP, topK, frequencyPenalty, presencePenalty, repetitionPenalty)
    },
    reasoning = reasoning?.let {
        ReasoningColumns(
            mandatory = it.mandatory,
            enabledByDefault = it.enabledByDefault,
            efforts = it.supportedEfforts.map(ReasoningEffort::wireName),
            defaultEffort = it.defaultEffort?.wireName,
            supportsBudget = it.supportsBudget,
        )
    },
    isModerated = isModerated,
)

/**
 * A row that cannot be read back, such as an unknown pricing kind from a newer build, means the stored
 * catalog is not this build's: `null` here makes the caller treat the catalog as missing and fetch it.
 */
internal fun CatalogModelEntity.toDomainOrNull(): AiModel? {
    val pricing = pricing.toDomainOrNull() ?: return null
    return AiModel(
        id = ModelId(id),
        name = name,
        description = description,
        contextLength = contextLength,
        maxCompletionTokens = maxCompletionTokens,
        pricing = pricing,
        inputModalities = inputModalities.toSet(),
        outputModalities = outputModalities.toSet(),
        supportedParameters = supportedParameters.mapTo(linkedSetOf(), ModelParameter::fromWireName),
        defaultParameters = with(defaults) {
            DefaultParameters(temperature, topP, topK, frequencyPenalty, presencePenalty, repetitionPenalty)
        },
        reasoning = reasoning?.let {
            ReasoningSupport(
                mandatory = it.mandatory,
                enabledByDefault = it.enabledByDefault,
                supportedEfforts = it.efforts.map(::ReasoningEffort),
                defaultEffort = it.defaultEffort?.let(::ReasoningEffort),
                supportsBudget = it.supportsBudget,
            )
        },
        isModerated = isModerated,
    )
}

private fun ModelPricing.toColumns() = when (this) {
    ModelPricing.Dynamic -> PricingColumns(PRICE_DYNAMIC, prompt = null, completion = null)
    ModelPricing.Unknown -> PricingColumns(PRICE_UNKNOWN, prompt = null, completion = null)
    ModelPricing.Free -> PricingColumns(PRICE_FREE, prompt = null, completion = null)
    is ModelPricing.Metered -> PricingColumns(
        PRICE_METERED,
        prompt = promptPerToken.toPlainString(),
        completion = completionPerToken.toPlainString(),
    )
}

private fun PricingColumns.toDomainOrNull(): ModelPricing? = when (kind) {
    PRICE_DYNAMIC -> ModelPricing.Dynamic
    PRICE_UNKNOWN -> ModelPricing.Unknown
    PRICE_FREE -> ModelPricing.Free
    PRICE_METERED -> {
        val promptPrice = prompt?.toBigDecimalOrNull()
        val completionPrice = completion?.toBigDecimalOrNull()
        if (promptPrice == null || completionPrice == null) null else ModelPricing.Metered(promptPrice, completionPrice)
    }
    else -> null
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (_: NumberFormatException) {
    null
}

internal fun Tuning.toColumns() = TuningColumns(
    temperature = temperature,
    topP = topP,
    topK = topK,
    minP = minP,
    topA = topA,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    repetitionPenalty = repetitionPenalty,
    maxTokens = maxTokens,
    seed = seed,
    stop = stop,
    verbosity = verbosity?.wireName,
    reasoning = when (val reasoning = reasoning) {
        null -> null
        Reasoning.Off -> REASONING_OFF
        is Reasoning.On -> reasoning.effort?.wireName ?: REASONING_ON
        is Reasoning.Budget -> REASONING_BUDGET
    },
    reasoningBudget = (reasoning as? Reasoning.Budget)?.maxTokens,
)

/**
 * A stored tuning that no longer passes [Tuning]'s checks (a range narrowed in a later build) comes back
 * [Tuning.Untouched] rather than failing: losing a preference is better than a screen that cannot open.
 */
internal fun TuningColumns.toDomain(): Tuning = try {
    Tuning(
        temperature = temperature,
        topP = topP,
        topK = topK,
        minP = minP,
        topA = topA,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        repetitionPenalty = repetitionPenalty,
        maxTokens = maxTokens,
        seed = seed,
        stop = stop,
        verbosity = verbosity?.let(Verbosity::fromWireName),
        reasoning = when (val stored = reasoning) {
            null -> null
            REASONING_OFF -> Reasoning.Off
            REASONING_ON -> Reasoning.On(effort = null)
            REASONING_BUDGET -> reasoningBudget?.let(Reasoning::Budget)
            else -> Reasoning.On(ReasoningEffort(stored))
        },
    )
} catch (_: IllegalArgumentException) {
    Tuning.Untouched
}
