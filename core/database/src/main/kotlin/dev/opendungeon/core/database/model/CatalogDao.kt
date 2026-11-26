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

    @Query("SELECT * FROM catalog_model ORDER BY position")
    public fun observeModels(): Flow<List<CatalogModelEntity>>

    @Query("SELECT * FROM catalog_fetch WHERE slot = 0")
    public fun observeFetch(): Flow<CatalogFetchEntity?>

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
