package dev.opendungeon.feature.newstory.domain.seeded

import dev.opendungeon.domain.drafting.Seed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledSeedDeckTest {

    private val seeds = BundledSeedDeck().seeds

    @Test
    fun `every bundled seed is whole, and there are enough to play a while before a model writes any`() {
        assertTrue(seeds.size >= 30, "only ${seeds.size} seeds")
    }

    @Test
    fun `no two bundled seeds share a template`() {
        assertEquals(seeds.size, seeds.map { it.template.lowercase() }.toSet().size)
    }

    @Test
    fun `every blank has words to reroll before a model is asked for more`() {
        val thin = seeds.flatMap { seed ->
            seed.blanks.filter { it.unused.size < MIN_UNUSED }.map { "${seed.template} ${it.key}" }
        }
        assertEquals(emptyList(), thin)
    }

    @Test
    fun `sentences read as sentences, and a blank opening one is capitalised`() {
        seeds.forEach { seed ->
            assertTrue(seed.text.first().isUpperCase(), seed.text)
            assertTrue(seed.text.last() in ".!?", seed.text)
            assertTrue(seed.blanks.all { it.fill.length <= Seed.MAX_FILL_LENGTH }, seed.text)
        }
    }

    private companion object {
        const val MIN_UNUSED = 4
    }
}
