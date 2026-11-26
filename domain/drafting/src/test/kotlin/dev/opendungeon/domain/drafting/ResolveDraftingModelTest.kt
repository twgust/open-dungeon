package dev.opendungeon.domain.drafting

import app.cash.turbine.test
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.openrouter.Catalog
import dev.opendungeon.domain.openrouter.testing.FETCHED_AT
import dev.opendungeon.domain.openrouter.testing.FakeModelCatalog
import dev.opendungeon.domain.openrouter.testing.FakeModelSetup
import dev.opendungeon.domain.openrouter.testing.aiModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveDraftingModelTest {

    private val storyteller = aiModel("acme/storyteller")
    private val poet = aiModel("acme/poet")
    private val catalog = FakeModelCatalog(Catalog(listOf(storyteller, poet), FETCHED_AT))

    @Test
    fun `the chosen model drafts, as tuned`() = runTest {
        val setup = FakeModelSetup(chosen = storyteller.id)
        setup.storeTuning(storyteller.id, Tuning(temperature = 1.1))

        DefaultResolveDraftingModel(setup, catalog)().test {
            assertEquals(DraftingModel(storyteller, Tuning(temperature = 1.1)), awaitItem())
        }
    }

    @Test
    fun `nothing drafts before a model is chosen, and the first choice drafts`() = runTest {
        val setup = FakeModelSetup()

        DefaultResolveDraftingModel(setup, catalog)().test {
            assertNull(awaitItem())
            setup.choose(poet.id)
            assertEquals(DraftingModel(poet, Tuning.Untouched), awaitItem())
        }
    }

    @Test
    fun `a chosen model the catalog no longer offers cannot draft`() = runTest {
        val setup = FakeModelSetup(chosen = ModelId("gone/model"))

        DefaultResolveDraftingModel(setup, catalog)().test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `a new tuning reaches drafting at once`() = runTest {
        val setup = FakeModelSetup(chosen = storyteller.id)

        DefaultResolveDraftingModel(setup, catalog)().test {
            assertEquals(Tuning.Untouched, awaitItem()?.tuning)
            setup.storeTuning(storyteller.id, Tuning(topP = 0.5))
            assertEquals(Tuning(topP = 0.5), awaitItem()?.tuning)
        }
    }
}
