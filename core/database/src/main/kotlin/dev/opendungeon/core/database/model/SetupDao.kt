package dev.opendungeon.core.database.model

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The chosen model and the tuning of each model. */
@Dao
public interface SetupDao {

    @Query("SELECT model_id FROM chosen_model WHERE slot = 0")
    public fun observeChosen(): Flow<String?>

    @Upsert
    public suspend fun upsertChosen(chosen: ChosenModelEntity)

    @Query("SELECT * FROM model_tuning WHERE model_id = :modelId")
    public fun observeTuning(modelId: String): Flow<ModelTuningEntity?>

    @Upsert
    public suspend fun upsertTuning(tuning: ModelTuningEntity)
}
