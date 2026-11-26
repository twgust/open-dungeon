package dev.opendungeon.data.openrouter

import dev.opendungeon.core.credentials.ApiKeyStore
import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ApiKey
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.KeyInfo
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.ReasoningSupport
import dev.opendungeon.core.network.NetworkError
import dev.opendungeon.core.network.NetworkResult
import dev.opendungeon.core.network.dto.CurrentKeyDto
import dev.opendungeon.core.network.dto.ModelDto
import dev.opendungeon.core.network.dto.PricingDto
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

internal suspend fun <T> ApiKeyStore.withApiKey(
    block: suspend (ApiKey) -> Outcome<T, DataError>,
): Outcome<T, DataError> = apiKey.first()?.let { block(it) } ?: Outcome.Failure(DataError.MissingApiKey)

internal inline fun <T, R> NetworkResult<T>.toDataOutcome(transform: (T) -> R): Outcome<R, DataError> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> Outcome.Failure(error.toDataError())
}

internal fun NetworkError.toDataError(): DataError = when (this) {
    is NetworkError.Transport -> DataError.Network
    is NetworkError.Decoding -> DataError.UnexpectedResponse
    is NetworkError.Http -> DataError.fromHttpStatus(status, body?.message.orEmpty())
}

internal fun CurrentKeyDto.toKeyInfo() = KeyInfo(
    label = label,
    isFreeTier = isFreeTier,
    usage = usage,
    limit = limit,
    limitRemaining = limitRemaining,
)

/** Every listed model is kept: one whose prices cannot be read is still selectable, priced [ModelPricing.Unknown]. */
internal fun ModelDto.toAiModel(): AiModel =
    AiModel(
        id = ModelId(id),
        name = name,
        description = description,
        contextLength = topProvider?.contextLength ?: contextLength,
        maxCompletionTokens = topProvider?.maxCompletionTokens,
        pricing = pricing.toModelPricing(),
        inputModalities = architecture?.inputModalities.orEmpty().toSet(),
        outputModalities = architecture?.outputModalities.orEmpty().toSet(),
        supportedParameters = supportedParameters.mapTo(linkedSetOf(), ModelParameter::fromWireName),
        defaultParameters = defaultParameters?.let {
            DefaultParameters(
                temperature = it.temperature,
                topP = it.topP,
                topK = it.topK,
                frequencyPenalty = it.frequencyPenalty,
                presencePenalty = it.presencePenalty,
                repetitionPenalty = it.repetitionPenalty,
            )
        } ?: DefaultParameters(),
        reasoning = reasoning?.let {
            ReasoningSupport(
                mandatory = it.mandatory,
                enabledByDefault = it.defaultEnabled,
                supportedEfforts = it.supportedEfforts,
                defaultEffort = it.defaultEffort,
            )
        },
    )

private val DYNAMIC_PRICE = BigDecimal("-1")

internal fun PricingDto.toModelPricing(): ModelPricing {
    val promptPrice = prompt.toBigDecimalOrNull() ?: return ModelPricing.Unknown
    val completionPrice = completion.toBigDecimalOrNull() ?: return ModelPricing.Unknown
    return when {
        promptPrice.compareTo(DYNAMIC_PRICE) == 0 || completionPrice.compareTo(DYNAMIC_PRICE) == 0 -> ModelPricing.Dynamic
        promptPrice.signum() == 0 && completionPrice.signum() == 0 -> ModelPricing.Free
        else -> ModelPricing.Metered(promptPerToken = promptPrice, completionPerToken = completionPrice)
    }
}
