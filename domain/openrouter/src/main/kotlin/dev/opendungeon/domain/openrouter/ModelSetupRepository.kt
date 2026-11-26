package dev.opendungeon.domain.openrouter

import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import kotlinx.coroutines.flow.Flow

/**
 * Which model the player writes with, and how they tuned each model they have tried. Tunings are kept
 * per model, so switching away and back finds a model as it was left.
 */
public interface ModelSetupRepository {

    /** The chosen model's id, or `null` before the first choice. It may name a model no longer offered. */
    public val chosenModel: Flow<ModelId?>

    public suspend fun choose(id: ModelId): Outcome<Unit, DataError>

    /** [id]'s tuning, [Tuning.Untouched] when it was never tuned. Emits on every change. */
    public fun tuning(id: ModelId): Flow<Tuning>

    public suspend fun saveTuning(id: ModelId, tuning: Tuning): Outcome<Unit, DataError>
}
