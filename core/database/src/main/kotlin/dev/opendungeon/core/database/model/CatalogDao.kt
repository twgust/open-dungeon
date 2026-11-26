package dev.opendungeon.core.database.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The stored catalog: every model in OpenRouter's order, and when it was fetched. */
@Dao
public interface CatalogDao {

    /**
     * The stored catalog in one read, so a write in a transaction is seen whole: one row per model in
     * OpenRouter's order, each carrying when the catalog was fetched; a single row without a model when
     * the fetched catalog is empty; no rows when nothing was fetched. Two separate flows would each
     * re-query on their own and could show models from one catalog beside the fetch of another.
     */
    @Query(
        "SELECT catalog_fetch.fetched_at_millis, catalog_model.* FROM catalog_fetch " +
            "LEFT JOIN catalog_model ON 1 = 1 WHERE catalog_fetch.slot = 0 ORDER BY catalog_model.position",
    )
    public fun observeCatalog(): Flow<List<StoredCatalogRow>>

    /** Replaces the catalog whole, so readers never see half of one. Rankings belong to the old one and go too. */
    @Transaction
    public suspend fun replaceCatalog(models: List<CatalogModelEntity>, fetch: CatalogFetchEntity) {
        clearCatalog()
        insertModels(models)
        upsertFetch(fetch)
    }

    /** Forgets the catalog and the rankings made from it. */
    @Transaction
    public suspend fun clearCatalog() {
        deleteModels()
        deleteFetch()
        deleteRanks()
    }

    @Insert
    public suspend fun insertModels(models: List<CatalogModelEntity>)

    @Upsert
    public suspend fun upsertFetch(fetch: CatalogFetchEntity)

    @Query("DELETE FROM catalog_model")
    public suspend fun deleteModels()

    @Query("DELETE FROM catalog_fetch")
    public suspend fun deleteFetch()

    @Query("DELETE FROM catalog_rank")
    public suspend fun deleteRanks()
}
