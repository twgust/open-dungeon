package dev.opendungeon.tools.continuity.model

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.data.openrouter.RemoteModelCatalog
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Finds models in OpenRouter's live catalog for the key in use, so every run uses the model's real
 * parameters and structured-output support, and a retired id fails before any money is spent.
 */
internal interface ModelResolver {
    suspend fun resolve(ids: Set<ModelId>): Outcome<Map<ModelId, AiModel>, ResolveError>
}

internal sealed interface ResolveError {
    data class Access(val error: DataError) : ResolveError

    /** [missing] is not offered to this key; [similar] are ids that look close, to suggest instead. */
    data class Unknown(val missing: ModelId, val similar: List<ModelId>) : ResolveError
}

@Inject
@ContributesBinding(AppScope::class)
internal class CatalogModelResolver(private val catalog: RemoteModelCatalog) : ModelResolver {

    override suspend fun resolve(ids: Set<ModelId>): Outcome<Map<ModelId, AiModel>, ResolveError> {
        val models = when (val result = catalog.fetchModels(sort = null)) {
            is Outcome.Failure -> return Outcome.Failure(ResolveError.Access(result.error))
            is Outcome.Success -> result.value.associateBy { it.id }
        }
        ids.firstOrNull { it !in models }?.let { missing ->
            return Outcome.Failure(ResolveError.Unknown(missing, similarTo(missing, models.keys)))
        }
        return Outcome.Success(ids.associateWith { models.getValue(it) })
    }

    /** Ids from the same author whose name shares the first word of the missing one, e.g. other `deepseek-v4` variants. */
    private fun similarTo(missing: ModelId, offered: Set<ModelId>): List<ModelId> {
        val author = missing.value.substringBefore('/')
        val family = missing.value.substringAfter('/').substringBefore('-')
        return offered.filter { it.value.startsWith("$author/$family") }.sortedBy { it.value }.take(MAX_SUGGESTIONS)
    }

    private companion object {
        const val MAX_SUGGESTIONS = 8
    }
}
