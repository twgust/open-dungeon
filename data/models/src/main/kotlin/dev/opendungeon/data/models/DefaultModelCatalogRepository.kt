package dev.opendungeon.data.models

import dev.opendungeon.core.database.model.CatalogDao
import dev.opendungeon.core.database.model.CatalogFetchEntity
import dev.opendungeon.core.database.model.CatalogRankEntity
import dev.opendungeon.core.database.model.RankingDao
import dev.opendungeon.core.database.model.StoredCatalogRow
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelEndpoint
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.flatMap
import dev.opendungeon.data.openrouter.RemoteModelCatalog
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.ModelCatalogRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/** The catalog in Room, filled from [remote]. Reads never touch the network. */
@Inject
@ContributesBinding(AppScope::class)
public class DefaultModelCatalogRepository(
    private val catalogDao: CatalogDao,
    private val rankingDao: RankingDao,
    private val remote: RemoteModelCatalog,
    private val clock: Clock,
) : ModelCatalogRepository {

    override val catalog: Flow<Catalog?> = catalogDao.observeCatalog().map { rows ->
        val fetch = rows.firstOrNull() ?: return@map null
        val stored = rows.mapNotNull(StoredCatalogRow::model)
        val models = stored.mapNotNull { it.toDomainOrNull() }
        // A row that no longer reads back is a catalog that has to be fetched again, not a shorter one.
        if (models.size != stored.size) return@map null
        Catalog(models, Instant.fromEpochMilliseconds(fetch.fetchedAtMillis))
    }.distinctUntilChanged()

    override suspend fun refresh(): Outcome<Unit, DataError> = remote.fetchModels(sort = null).flatMap { models ->
        val rows = models.mapIndexed { position, model -> model.toEntity(position) }
        storage {
            catalogDao.replaceCatalog(rows, CatalogFetchEntity(fetchedAtMillis = clock.now().toEpochMilliseconds()))
        }
    }

    override suspend fun ranking(sort: ModelSort): Outcome<List<ModelId>, DataError> {
        val stored = when (val read = storage { rankingDao.ranking(sort.wireName) }) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        if (stored.isNotEmpty()) return Outcome.Success(stored.map(::ModelId))
        return remote.fetchModels(sort).flatMap { models ->
            val ranks = models.mapIndexed {
                position,
                model,
                ->
                CatalogRankEntity(sort.wireName, position, model.id.value)
            }
            storage {
                rankingDao.replaceRanking(sort.wireName, ranks)
            }.flatMap { Outcome.Success(models.map { it.id }) }
        }
    }

    override suspend fun endpoints(id: ModelId): Outcome<List<ModelEndpoint>, DataError> = remote.fetchEndpoints(id)

    override suspend fun clear(): Outcome<Unit, DataError> = storage { catalogDao.clearCatalog() }
}
