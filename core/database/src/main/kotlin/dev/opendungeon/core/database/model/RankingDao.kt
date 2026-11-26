package dev.opendungeon.core.database.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** OpenRouter's rankings of the stored catalog, one ordered list of ids per sort. */
@Dao
public interface RankingDao {

    @Query("SELECT model_id FROM catalog_rank WHERE sort = :sort ORDER BY position")
    public suspend fun ranking(sort: String): List<String>

    @Transaction
    public suspend fun replaceRanking(sort: String, ranks: List<CatalogRankEntity>) {
        deleteRanking(sort)
        insertRanks(ranks)
    }

    @Insert
    public suspend fun insertRanks(ranks: List<CatalogRankEntity>)

    @Query("DELETE FROM catalog_rank WHERE sort = :sort")
    public suspend fun deleteRanking(sort: String)
}
