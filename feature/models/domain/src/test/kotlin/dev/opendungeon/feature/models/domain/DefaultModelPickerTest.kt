package dev.opendungeon.feature.models.domain

import app.cash.turbine.test
import dev.opendungeon.core.model.DataError
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.ModelPricing
import dev.opendungeon.core.model.ModelSort
import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.testing.FETCHED_AT
import dev.opendungeon.domain.openrouter.testing.FakeModelCatalog
import dev.opendungeon.domain.openrouter.testing.FakeModelSetup
import dev.opendungeon.domain.openrouter.testing.aiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultModelPickerTest {

    private val hermes = aiModel(
        "nous/hermes-4-405b",
        name = "Hermes 4 405B",
        parameters = listOf("temperature", "tools"),
        description = "Frontier reasoning, open weights",
    )
    private val gemma = aiModel(
        "google/gemma-3-27b-it:free",
        name = "Gemma 3 27B (free)",
        pricing = ModelPricing.Free,
        contextLength = 96_000,
        inputs = setOf("text", "image"),
    )
    private val gemini = aiModel(
        "google/gemini-3-pro",
        name = "Gemini 3 Pro",
        contextLength = 1_048_576,
        inputs = setOf("text", "image", "audio", "file"),
        parameters = listOf("tools"),
    )
    private val models = listOf(hermes, gemma, gemini)

    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private fun TestScope.picker(
        catalog: FakeModelCatalog = FakeModelCatalog(Catalog(models, FETCHED_AT)),
        setup: FakeModelSetup = FakeModelSetup(),
        now: Instant = FETCHED_AT + 1.hours,
    ) = DefaultModelPicker(backgroundScope, catalog, setup, FixedClock(now))

    private fun PickerState.ids() = models.map { it.id.value }

    @Test
    fun `a fresh catalog shows at once and is not fetched again`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(Catalog(models, FETCHED_AT))
        val state = picker(catalog).state.value

        assertEquals(CatalogStatus.Ready, state.catalog)
        assertEquals(listOf(hermes, gemma, gemini), state.models)
        assertEquals(3, state.total)
        assertEquals(listOf("image", "audio", "file"), state.inputModalities)
        assertEquals(0, catalog.refreshes)
    }

    @Test
    fun `a catalog older than a day is fetched again on opening`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(Catalog(models, FETCHED_AT))

        picker(catalog, now = FETCHED_AT + 25.hours)

        assertEquals(1, catalog.refreshes)
    }

    @Test
    fun `without a catalog it loads, and a failure shows in place of the list`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog()
        val answer = catalog.hold()
        val picker = picker(catalog)
        assertEquals(CatalogStatus.Loading, picker.state.value.catalog)
        assertTrue(picker.state.value.isRefreshing)

        answer.complete(Outcome.Failure(DataError.Network))

        assertEquals(CatalogStatus.Missing(DataError.Network), picker.state.value.catalog)
        assertFalse(picker.state.value.isRefreshing)
        catalog.refreshResult = Outcome.Success(models)
        picker.refresh()
        assertEquals(CatalogStatus.Ready, picker.state.value.catalog)
    }

    @Test
    fun `a failed refresh over a catalog keeps it and says so`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(Catalog(models, FETCHED_AT)).apply {
            refreshResult = Outcome.Failure(DataError.RateLimited("Slow down"))
        }
        val picker = picker(catalog)

        picker.events.test {
            picker.refresh()

            assertEquals(PickerEvent.Failed(DataError.RateLimited("Slow down")), awaitItem())
        }
        assertEquals(3, picker.state.value.models.size)
    }

    @Test
    fun `a refresh while one runs is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(Catalog(models, FETCHED_AT))
        val answer = catalog.hold()
        val picker = picker(catalog)

        picker.refresh()
        picker.refresh()
        answer.complete(Outcome.Success(models))

        assertEquals(1, catalog.refreshes)
    }

    @Test
    fun `search matches every word in name, id or description, ignoring case`() = runTest(UnconfinedTestDispatcher()) {
        val picker = picker()

        picker.search("GOOGLE  free")
        assertEquals(listOf(gemma.id.value), picker.state.value.ids())
        picker.search("open weights")
        assertEquals(listOf(hermes.id.value), picker.state.value.ids())
        picker.search("nothing like this")
        assertEquals(emptyList(), picker.state.value.ids())
        assertEquals(3, picker.state.value.total)
    }

    @Test
    fun `filters narrow the list and toggle back`() = runTest(UnconfinedTestDispatcher()) {
        val picker = picker()

        picker.toggleFreeOnly()
        assertEquals(listOf(gemma.id.value), picker.state.value.ids())
        picker.toggleFreeOnly()

        picker.toggleTools()
        assertEquals(listOf(hermes.id.value, gemini.id.value), picker.state.value.ids())
        picker.toggleTools()

        picker.toggleInput("image")
        assertEquals(listOf(gemma.id.value, gemini.id.value), picker.state.value.ids())
        picker.toggleInput("audio")
        assertEquals(listOf(gemini.id.value), picker.state.value.ids(), "every picked input must be read")
        picker.toggleInput("image")
        picker.toggleInput("audio")

        picker.toggleContext(32_000)
        picker.toggleContext(128_000)
        assertEquals(listOf(hermes.id.value, gemini.id.value), picker.state.value.ids())
        picker.toggleContext(128_000)
        assertEquals(3, picker.state.value.models.size)
    }

    @Test
    fun `a sort shows at once, is ranked by OpenRouter once, and lists models it left out last`() =
        runTest(UnconfinedTestDispatcher()) {
            val catalog = FakeModelCatalog(Catalog(models, FETCHED_AT))
            val ranking = CompletableDeferred<Outcome<List<ModelId>, DataError>>().also(catalog.heldRankings::addLast)
            val picker = picker(catalog)

            picker.sortBy(ModelSort.TopWeekly)
            assertEquals(ModelSort.TopWeekly, picker.state.value.sort)
            assertTrue(picker.state.value.isSorting)
            assertEquals(listOf(hermes, gemma, gemini), picker.state.value.models, "the old order until ranked")

            ranking.complete(Outcome.Success(listOf(gemini.id, hermes.id)))
            assertFalse(picker.state.value.isSorting)
            assertEquals(listOf(gemini, hermes, gemma), picker.state.value.models)

            picker.sortBy(null)
            picker.sortBy(ModelSort.TopWeekly)
            assertEquals(listOf(ModelSort.TopWeekly), catalog.rankedSorts)
            assertEquals(listOf(gemini, hermes, gemma), picker.state.value.models)
        }

    @Test
    fun `a sort OpenRouter could not rank goes back to the last one that worked`() =
        runTest(UnconfinedTestDispatcher()) {
            val catalog = FakeModelCatalog(Catalog(models, FETCHED_AT)).apply {
                rankingResult = { sort ->
                    if (sort ==
                        ModelSort.Newest
                    ) {
                        Outcome.Success(listOf(gemma.id))
                    } else {
                        Outcome.Failure(DataError.Network)
                    }
                }
            }
            val picker = picker(catalog)
            picker.sortBy(ModelSort.Newest)

            picker.events.test {
                picker.sortBy(ModelSort.Latency)

                assertEquals(PickerEvent.Failed(DataError.Network), awaitItem())
            }
            assertEquals(ModelSort.Newest, picker.state.value.sort)
            assertEquals(gemma, picker.state.value.models.first())
        }

    @Test
    fun `a new catalog drops the rankings and ranks the current sort again`() = runTest(UnconfinedTestDispatcher()) {
        val catalog = FakeModelCatalog(Catalog(models, FETCHED_AT)).apply {
            rankingResult = { Outcome.Success(listOf(gemma.id)) }
        }
        val picker = picker(catalog)
        picker.sortBy(ModelSort.Newest)

        catalog.fetchedAt = FETCHED_AT + 2.hours
        catalog.refreshResult = Outcome.Success(models)
        picker.refresh()

        assertEquals(listOf(ModelSort.Newest, ModelSort.Newest), catalog.rankedSorts)
    }

    @Test
    fun `a choice shows at once and is reported once stored`() = runTest(UnconfinedTestDispatcher()) {
        val setup = FakeModelSetup().apply { heldChoice = CompletableDeferred() }
        val picker = picker(setup = setup)

        picker.events.test {
            picker.choose(gemma.id)
            assertEquals(gemma.id, picker.state.value.chosen)

            setup.heldChoice?.complete(Unit)
            assertEquals(PickerEvent.Chosen(gemma.id), awaitItem())
        }
        assertEquals(gemma.id, picker.state.value.chosen)
    }

    @Test
    fun `a choice the device cannot store is taken back`() = runTest(UnconfinedTestDispatcher()) {
        val setup = FakeModelSetup(chosen = hermes.id).apply { failure = DataError.Storage }
        val picker = picker(setup = setup)

        picker.events.test {
            picker.choose(gemma.id)

            assertEquals(PickerEvent.Failed(DataError.Storage), awaitItem())
        }
        assertEquals(hermes.id, picker.state.value.chosen)
    }
}
