package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.feature.newstory.domain.vocabulary.DefaultRollChoice
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RollChoiceTest {

    private val roll = DefaultRollChoice(Random(7))

    @Test
    fun `a roll never repeats what is avoided while there is something else`() {
        repeat(50) {
            assertEquals(
                listOf("a drowned city"),
                roll(listOf("a salt-war border town", "a drowned city"), avoiding = listOf("a salt-war border town")),
            )
        }
    }

    @Test
    fun `a roll for more than is fresh tops up with avoided words, all different`() {
        val words = listOf("Gothic", "Noir", "Western", "Horror")

        val genres = roll(words, count = 4, avoiding = listOf("Gothic", "Noir", "Western"))

        assertEquals("Horror", genres.first())
        assertEquals(words.toSet(), genres.toSet())
    }

    @Test
    fun `a word offered twice is still rolled once`() {
        assertEquals(listOf("Noir"), roll(listOf("Noir", "Noir"), count = 2))
    }
}
