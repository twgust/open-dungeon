package dev.opendungeon.data.models

import dev.opendungeon.core.database.model.ChosenModelEntity
import dev.opendungeon.core.database.model.ModelTuningEntity
import dev.opendungeon.core.database.model.SetupDao
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.openrouter.ModelSetupRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The chosen model and every tuning, in Room. */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultModelSetupRepository(private val dao: SetupDao) : ModelSetupRepository {

    override val chosenModel: Flow<ModelId?> = dao.observeChosen().map { it?.let(::ModelId) }.distinctUntilChanged()

    override suspend fun choose(id: ModelId): Outcome<Unit, DataError> =
        storage { dao.upsertChosen(ChosenModelEntity(modelId = id.value)) }

    override fun tuning(id: ModelId): Flow<Tuning> =
        dao.observeTuning(id.value).map { it?.tuning?.toDomain() ?: Tuning.Untouched }.distinctUntilChanged()

    override suspend fun saveTuning(id: ModelId, tuning: Tuning): Outcome<Unit, DataError> =
        storage { dao.upsertTuning(ModelTuningEntity(modelId = id.value, tuning = tuning.toColumns())) }
}
