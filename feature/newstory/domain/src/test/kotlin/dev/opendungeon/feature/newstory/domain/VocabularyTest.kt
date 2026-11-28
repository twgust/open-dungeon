package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.Register
import dev.opendungeon.feature.newstory.domain.vocabulary.BundledWizardVocabulary
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bundled words are data anyone may edit, so these guard what the rules rely on. */
class VocabularyTest {

    private val vocabulary = BundledWizardVocabulary()

    @Test
    fun `every list has words, none blank and none repeated across its groups`() {
        VocabularyList.entries.forEach { list ->
            val words = vocabulary[list]
            assertTrue(words.size >= 2, "$list needs at least two words for a reroll to change anything")
            assertEquals(
                emptyList(),
                words.groupBy {
                    it.lowercase()
                }.filterValues { it.size > 1 }.keys.toList(),
                "$list repeats",
            )
            assertTrue(words.none(String::isBlank), "$list has a blank word")
        }
    }

    @Test
    fun `labels within a list are distinct`() {
        VocabularyList.entries.forEach { list ->
            val labels = vocabulary.groups(list).map { it.label }
            assertEquals(labels.size, labels.toSet().size, "$list repeats a label")
        }
    }

    @Test
    fun `every register has its own words on every step, so a die in it is never empty`() {
        val lists = VocabularyList.entries - VocabularyList.Genres
        Register.entries.forEach { register ->
            lists.forEach { list ->
                val words = vocabulary.groups(list).filter { register in it.registers }.flatMap { it.words }
                assertTrue(words.size >= 2, "$list has nothing for $register")
            }
        }
    }

    @Test
    fun `every register has a genre that opens it`() {
        val registers = vocabulary.groups(VocabularyList.Genres).flatMap { it.registers }.toSet()
        assertEquals(Register.entries.toSet(), registers)
    }

    @Test
    fun `the roles and settings are plentiful`() {
        assertTrue(vocabulary[VocabularyList.Roles].size >= 250, "roles: ${vocabulary[VocabularyList.Roles].size}")
        assertTrue(
            vocabulary[VocabularyList.Settings].size >= 150,
            "settings: ${vocabulary[VocabularyList.Settings].size}",
        )
    }
}
