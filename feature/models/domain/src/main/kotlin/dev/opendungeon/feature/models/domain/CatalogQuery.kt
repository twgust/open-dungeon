package dev.opendungeon.feature.models.domain

import dev.opendungeon.core.model.AiModel
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelParameter.Known
import dev.opendungeon.core.model.ModelPricing

private const val TEXT = "text"

/**
 * The models that pass [filters]. The query matches name, id and description, ignoring case; every word
 * of it has to appear somewhere.
 */
internal fun List<AiModel>.matching(filters: ModelFilters): List<AiModel> {
    val words = filters.query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    return filter { model ->
        (!filters.freeOnly || model.pricing == ModelPricing.Free) &&
            (!filters.tools || Known.Tools in model.supportedParameters) &&
            (filters.minContext == null || (model.contextLength ?: 0) >= filters.minContext) &&
            model.inputModalities.containsAll(filters.inputs) &&
            words.all { word -> model.mentions(word) }
    }
}

private fun AiModel.mentions(word: String): Boolean = name.lowercase().contains(word) ||
    id.value.lowercase().contains(word) ||
    description?.lowercase()?.contains(word) == true

/** [ranking]'s order; models it leaves out, such as ones added since, follow in their own order. */
internal fun List<AiModel>.rankedBy(ranking: List<ModelId>): List<AiModel> {
    val rank = ranking.withIndex().associate { (index, id) -> id to index }
    return sortedBy { rank[it.id] ?: Int.MAX_VALUE }
}

/** Input modalities besides text that some model reads, most common first. */
internal fun List<AiModel>.inputModalities(): List<String> = flatMap { it.inputModalities }
    .filter { it != TEXT }
    .groupingBy { it }
    .eachCount()
    .entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .map { it.key }
