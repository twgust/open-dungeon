package dev.opendungeon.data.models

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.opendungeon.core.database.OpenDungeonDatabase
import dev.opendungeon.core.database.model.CatalogModelEntity
import dev.opendungeon.core.database.model.PricingColumns
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.openrouter.Catalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class DefaultModelCatalogRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        OpenDungeonDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val remote = FakeRemoteCatalog()
    private val clock = FixedClock(Instant.parse("2026-11-26T10:00:00Z"))
    private val repository = DefaultModelCatalogRepository(database.catalogDao(), database.rankingDao(), remote, clock)

    @After
    fun close() = database.close()

    @Test
    fun `nothing is cached before the first refresh`() = runTest {
        assertNull(repository.catalog.first())
    }

    @Test
    fun `a refresh stores every model exactly, in OpenRouter's order, with when it was fetched`() = runTest {
        val models = listOf(aiModel("b/second", METERED), aiModel("a/first"))
        remote.models = { Outcome.Success(models) }

        assertEquals(Outcome.Success(Unit), repository.refresh())

        assertEquals(Catalog(models, clock.now), repository.catalog.first())
    }

    @Test
    fun `a refresh replaces the whole catalog and drops rankings made from the old one`() = runTest {
        remote.models = { Outcome.Success(listOf(aiModel("a/old"))) }
        repository.refresh()
        repository.ranking(ModelSort.Newest)
        remote.models = { Outcome.Success(listOf(aiModel("a/new"))) }

        repository.refresh()

        assertEquals(listOf(ModelId("a/new")), repository.catalog.first()?.models?.map { it.id })
        assertEquals(Outcome.Success(listOf(ModelId("a/new"))), repository.ranking(ModelSort.Newest))
        assertEquals(listOf(null, ModelSort.Newest, null, ModelSort.Newest), remote.requestedSorts)
    }

    @Test
    fun `a failed refresh keeps the cached catalog`() = runTest {
        remote.models = { Outcome.Success(listOf(aiModel("a/kept"))) }
        repository.refresh()
        remote.models = { Outcome.Failure(DataError.Network) }

        assertEquals(Outcome.Failure(DataError.Network), repository.refresh())

        assertEquals(listOf(ModelId("a/kept")), repository.catalog.first()?.models?.map { it.id })
    }

    @Test
    fun `a ranking is fetched once and then read from the cache`() = runTest {
        remote.models = { sort ->
            Outcome.Success(if (sort == null) emptyList() else listOf(aiModel("b/top"), aiModel("a/next")))
        }

        val first = repository.ranking(ModelSort.TopWeekly)
        val second = repository.ranking(ModelSort.TopWeekly)

        val expected = Outcome.Success(listOf(ModelId("b/top"), ModelId("a/next")))
        assertEquals(expected, first)
        assertEquals(expected, second)
        assertEquals(listOf<ModelSort?>(ModelSort.TopWeekly), remote.requestedSorts)
    }

    @Test
    fun `a failed ranking fetch is reported and nothing is cached`() = runTest {
        remote.models = { Outcome.Failure(DataError.RateLimited("Slow down")) }

        assertEquals(Outcome.Failure(DataError.RateLimited("Slow down")), repository.ranking(ModelSort.Latency))
        remote.models = { Outcome.Success(listOf(aiModel("a/fast"))) }
        assertEquals(Outcome.Success(listOf(ModelId("a/fast"))), repository.ranking(ModelSort.Latency))
    }

    @Test
    fun `clearing forgets the catalog`() = runTest {
        remote.models = { Outcome.Success(listOf(aiModel("a/gone"))) }
        repository.refresh()

        repository.catalog.test {
            assertEquals(1, awaitItem()?.models?.size)
            assertEquals(Outcome.Success(Unit), repository.clear())
            assertNull(awaitItem())
        }
    }

    @Test
    fun `a row this build cannot read makes the catalog missing, so it is fetched again`() = runTest {
        remote.models = { Outcome.Success(listOf(aiModel("a/fine"))) }
        repository.refresh()
        database.catalogDao().insertModels(
            listOf(aiModel("z/odd").toEntity(position = 1).withPricing(PricingColumns("subscription", null, null))),
        )

        assertNull(repository.catalog.first())
    }

    private fun CatalogModelEntity.withPricing(pricing: PricingColumns) = copy(pricing = pricing)
}
