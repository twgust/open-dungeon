package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.feature.newstory.domain.vocabulary.BundledWizardVocabulary
import dev.opendungeon.feature.newstory.domain.vocabulary.DefaultRollChoice
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RollChoiceTest {

    private val roll = DefaultRollChoice(TestVocabulary, Random(7))

    @Test
    fun `a roll never repeats what is avoided while there is something else`() {
        repeat(50) {
            assertEquals(
                listOf("a drowned city"),
                roll(VocabularyList.Settings, avoiding = listOf("a salt-war border town")),
            )
        }
    }

    @Test
    fun `a roll for more than is fresh tops up with avoided words, all different`() {
        val genres = roll(VocabularyList.Genres, count = 4, avoiding = listOf("Gothic", "Noir", "Western"))

        assertEquals("Horror", genres.first())
        assertEquals(TestVocabulary[VocabularyList.Genres].toSet(), genres.toSet())
    }

    @Test
    fun `the bundled vocabulary has every list, without repeats`() {
        val vocabulary = BundledWizardVocabulary()

        VocabularyList.entries.forEach { list ->
            val words = vocabulary[list]
            assertTrue(words.size >= 2, "$list needs at least two words for a reroll to change anything")
            assertEquals(words.size, words.toSet().size, "$list repeats a word")
            assertTrue(words.none(String::isBlank), "$list has a blank word")
        }
    }
}
