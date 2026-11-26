package dev.opendungeon.data.models

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.ReasoningEffort
import dev.opendungeon.core.model.ReasoningSupport
import dev.opendungeon.data.openrouter.RemoteModelCatalog
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant

internal fun aiModel(id: String, pricing: ModelPricing = ModelPricing.Free) = AiModel(
    id = ModelId(id),
    name = id.substringAfter('/'),
    description = "A model",
    contextLength = 131_072,
    maxCompletionTokens = 8_192,
    pricing = pricing,
    inputModalities = setOf("text", "image"),
    outputModalities = setOf("text"),
    supportedParameters = listOf("temperature", "reasoning", "brand_new_knob")
        .mapTo(linkedSetOf(), ModelParameter::fromWireName),
    defaultParameters = DefaultParameters(temperature = 0.7, topP = 0.95),
    reasoning = ReasoningSupport(
        mandatory = false,
        enabledByDefault = true,
        supportedEfforts = listOf(ReasoningEffort("high"), ReasoningEffort("low")),
        defaultEffort = ReasoningEffort("high"),
        supportsBudget = true,
    ),
    isModerated = null,
)

internal val METERED = ModelPricing.Metered(BigDecimal("0.0000012"), BigDecimal("0.000000000000000001"))

internal class FakeRemoteCatalog(
    var models: (ModelSort?) -> Outcome<List<AiModel>, DataError> = { error("unexpected fetchModels") },
) : RemoteModelCatalog {
    val requestedSorts = mutableListOf<ModelSort?>()

    override suspend fun fetchModels(sort: ModelSort?): Outcome<List<AiModel>, DataError> {
        requestedSorts += sort
        return models(sort)
    }

    override suspend fun fetchEndpoints(id: ModelId): Outcome<List<ModelEndpoint>, DataError> =
        Outcome.Success(emptyList())
}

internal class FixedClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
