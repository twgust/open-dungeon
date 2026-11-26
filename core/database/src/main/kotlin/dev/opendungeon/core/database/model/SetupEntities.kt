package dev.opendungeon.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** The model the player writes with. At most one row. */
@Entity(tableName = "chosen_model")
public data class ChosenModelEntity(
    @PrimaryKey val slot: Int = ONLY,
    @ColumnInfo(name = "model_id") val modelId: String,
) {
    public companion object {
        public const val ONLY: Int = 0
    }
}

/**
 * The player's tuning of one model. It is read and written whole and never queried into, so it is one
 * JSON column; a field added later decodes as unset.
 */
@Entity(tableName = "model_tuning")
public data class ModelTuningEntity(
    @PrimaryKey @ColumnInfo(name = "model_id") val modelId: String,
    val tuning: TuningColumns,
)

/** Wire names for every enumerated value, so renaming a Kotlin constant never changes stored data. */
@Serializable
public data class TuningColumns(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val minP: Double? = null,
    val topA: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val repetitionPenalty: Double? = null,
    val maxTokens: Int? = null,
    val seed: Long? = null,
    val stop: List<String> = emptyList(),
    val verbosity: String? = null,
    /** `"off"`, `"on"` for the model's default effort, `"budget"` for [reasoningBudget], or an effort's wire name. */
    val reasoning: String? = null,
    val reasoningBudget: Int? = null,
)
