package dev.opendungeon.domain.openrouter.testing

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.DefaultParameters
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.ReasoningSupport
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.opendungeon.domain.openrouter.ModelSetupRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/** When fakes say a catalog was fetched, unless a test says otherwise. */
public val FETCHED_AT: Instant = Instant.parse("2026-11-26T09:00:00Z")

/** A model with plain defaults; tests override what they look at. */
public fun aiModel(
    id: String,
    name: String = id.substringAfter('/'),
    parameters: List<String> = listOf("temperature", "max_tokens"),
    pricing: ModelPricing = ModelPricing.Metered("0.000001".toBigDecimal(), "0.000003".toBigDecimal()),
    contextLength: Int? = 131_072,
    maxCompletionTokens: Int? = 8_192,
    inputs: Set<String> = setOf("text"),
    description: String? = null,
    defaults: DefaultParameters = DefaultParameters(),
    reasoning: ReasoningSupport? = null,
): AiModel = AiModel(
    id = ModelId(id),
    name = name,
    description = description,
    contextLength = contextLength,
    maxCompletionTokens = maxCompletionTokens,
    pricing = pricing,
    inputModalities = inputs,
    outputModalities = setOf("text"),
    supportedParameters = parameters.mapTo(linkedSetOf(), ModelParameter::fromWireName),
    defaultParameters = defaults,
    reasoning = reasoning,
    isModerated = false,
)

/**
 * A catalog in memory. A refresh answers with [refreshResult], or with the next of [held] so a test decides
 * when it lands; a successful one stores the models it returned.
 */
public class FakeModelCatalog(initial: Catalog? = null) : ModelCatalogRepository {
    private val stored = MutableStateFlow(initial)
    override val catalog: Flow<Catalog?> = stored

    public var refreshResult: Outcome<List<AiModel>, DataError> = Outcome.Success(initial?.models.orEmpty())
    public val held: ArrayDeque<CompletableDeferred<Outcome<List<AiModel>, DataError>>> = ArrayDeque()
    public var fetchedAt: Instant = FETCHED_AT
    public var refreshes: Int = 0
        private set

    public var rankingResult: (ModelSort) -> Outcome<List<ModelId>, DataError> = { Outcome.Success(emptyList()) }
    public val heldRankings: ArrayDeque<CompletableDeferred<Outcome<List<ModelId>, DataError>>> = ArrayDeque()
    public val rankedSorts: MutableList<ModelSort> = mutableListOf()

    public var endpointsResult: Outcome<List<ModelEndpoint>, DataError> = Outcome.Success(emptyList())
    public val heldEndpoints: ArrayDeque<CompletableDeferred<Outcome<List<ModelEndpoint>, DataError>>> = ArrayDeque()

    public var clearResult: Outcome<Unit, DataError> = Outcome.Success(Unit)
    public var clears: Int = 0
        private set

    public fun hold(): CompletableDeferred<Outcome<List<AiModel>, DataError>> =
        CompletableDeferred<Outcome<List<AiModel>, DataError>>().also(held::addLast)

    public fun store(catalog: Catalog?) {
        stored.value = catalog
    }

    override suspend fun refresh(): Outcome<Unit, DataError> {
        refreshes++
        val result = held.removeFirstOrNull()?.await() ?: refreshResult
        return when (result) {
            is Outcome.Success -> {
                stored.value = Catalog(result.value, fetchedAt)
                Outcome.Success(Unit)
            }
            is Outcome.Failure -> result
        }
    }

    override suspend fun ranking(sort: ModelSort): Outcome<List<ModelId>, DataError> {
        rankedSorts += sort
        return heldRankings.removeFirstOrNull()?.await() ?: rankingResult(sort)
    }

    override suspend fun endpoints(id: ModelId): Outcome<List<ModelEndpoint>, DataError> =
        heldEndpoints.removeFirstOrNull()?.await() ?: endpointsResult

    override suspend fun clear(): Outcome<Unit, DataError> {
        clears++
        return clearResult.also { if (it is Outcome.Success) stored.value = null }
    }
}

/** The chosen model and tunings in memory. Writes fail with [failure] while it is set. */
public class FakeModelSetup(chosen: ModelId? = null) : ModelSetupRepository {
    private val chosenFlow = MutableStateFlow(chosen)
    override val chosenModel: Flow<ModelId?> = chosenFlow

    private val tunings = MutableStateFlow<Map<ModelId, Tuning>>(emptyMap())
    public val savedTunings: MutableList<Tuning> = mutableListOf()
    public var failure: DataError? = null

    /** Holds the next choice until completed, to see what shows before it is stored. */
    public var heldChoice: CompletableDeferred<Unit>? = null

    override suspend fun choose(id: ModelId): Outcome<Unit, DataError> {
        heldChoice?.await()
        failure?.let { return Outcome.Failure(it) }
        chosenFlow.value = id
        return Outcome.Success(Unit)
    }

    override fun tuning(id: ModelId): Flow<Tuning> = tunings.map { it[id] ?: Tuning.Untouched }.distinctUntilChanged()

    public fun storeTuning(id: ModelId, tuning: Tuning) {
        tunings.update { it + (id to tuning) }
    }

    override suspend fun saveTuning(id: ModelId, tuning: Tuning): Outcome<Unit, DataError> {
        failure?.let { return Outcome.Failure(it) }
        savedTunings += tuning
        storeTuning(id, tuning)
        return Outcome.Success(Unit)
    }
}
