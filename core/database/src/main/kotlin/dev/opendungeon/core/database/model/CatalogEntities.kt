package dev.opendungeon.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One catalog model. [position] keeps OpenRouter's default order. */
@Entity(tableName = "catalog_model")
public data class CatalogModelEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "context_length") val contextLength: Int?,
    @ColumnInfo(name = "max_completion_tokens") val maxCompletionTokens: Int?,
    @Embedded(prefix = "price_") val pricing: PricingColumns,
    @ColumnInfo(name = "input_modalities") val inputModalities: List<String>,
    @ColumnInfo(name = "output_modalities") val outputModalities: List<String>,
    /** Wire names, in the catalog's order. */
    @ColumnInfo(name = "supported_parameters") val supportedParameters: List<String>,
    @Embedded(prefix = "default_") val defaults: DefaultColumns,
    @Embedded(prefix = "reasoning_") val reasoning: ReasoningColumns?,
    @ColumnInfo(name = "is_moderated") val isModerated: Boolean?,
)

/** [kind] names the pricing case; the prices are the decimal strings OpenRouter sent, for metered models. */
public data class PricingColumns(val kind: String, val prompt: String?, val completion: String?)

public data class DefaultColumns(
    val temperature: Double?,
    @ColumnInfo(name = "top_p") val topP: Double?,
    @ColumnInfo(name = "top_k") val topK: Int?,
    @ColumnInfo(name = "frequency_penalty") val frequencyPenalty: Double?,
    @ColumnInfo(name = "presence_penalty") val presencePenalty: Double?,
    @ColumnInfo(name = "repetition_penalty") val repetitionPenalty: Double?,
)

public data class ReasoningColumns(
    val mandatory: Boolean,
    @ColumnInfo(name = "enabled_by_default") val enabledByDefault: Boolean,
    val efforts: List<String>,
    @ColumnInfo(name = "default_effort") val defaultEffort: String?,
    @ColumnInfo(name = "supports_budget") val supportsBudget: Boolean,
)

/** When the stored catalog was fetched. At most one row. */
@Entity(tableName = "catalog_fetch")
public data class CatalogFetchEntity(
    @PrimaryKey val slot: Int = ONLY,
    @ColumnInfo(name = "fetched_at_millis") val fetchedAtMillis: Long,
) {
    public companion object {
        public const val ONLY: Int = 0
    }
}

/** One row of [CatalogDao.observeCatalog]: when the catalog was fetched, and one of its models, if it has any. */
public data class StoredCatalogRow(
    @ColumnInfo(name = "fetched_at_millis") val fetchedAtMillis: Long,
    @Embedded val model: CatalogModelEntity?,
)

/** One place in OpenRouter's ranking for [sort], a `sort` wire value. */
@Entity(tableName = "catalog_rank", primaryKeys = ["sort", "position"])
public data class CatalogRankEntity(
    val sort: String,
    val position: Int,
    @ColumnInfo(name = "model_id") val modelId: String,
)
