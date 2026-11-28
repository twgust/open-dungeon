package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.policy.SettingPolicy
import dev.opendungeon.feature.newstory.domain.policy.StarringPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShelfTest {

    private val setting = SettingPolicy(TestVocabulary, FirstWordsRoll())
    private val starring = StarringPolicy(TestVocabulary, FirstWordsRoll(), CountingIds("you"))

    private fun draftOf(vararg genres: Phrase) = emptyDraft.copy(genres = genres.toList())

    private fun groupsOf(draft: StoryDraft): List<Pair<String?, Boolean>> =
        assertIs<StepBody.Words>(setting.body(draft)).groups.map { it.label to it.open }

    @Test
    fun `a story's genres open their groups first and fold the rest`() {
        assertEquals(
            listOf("Everyday" to true, "Dark" to false, "After dark" to false),
            groupsOf(draftOf(Phrase.Offered("Slice of life"))),
        )
    }

    @Test
    fun `with no genre that names a register, everything opens but after dark`() {
        val expected = listOf("Dark" to true, "Everyday" to true, "After dark" to false)
        assertEquals(expected, groupsOf(emptyDraft))
        assertEquals(expected, groupsOf(draftOf(Phrase.Own("cosy ghosts"))))
    }

    @Test
    fun `an adult genre opens after dark`() {
        assertEquals(
            listOf("After dark" to true, "Dark" to false, "Everyday" to false),
            groupsOf(draftOf(Phrase.Offered("Erotic"))),
        )
    }

    @Test
    fun `an adult genre's groups lead the other genre's`() {
        assertEquals(
            listOf("After dark" to true, "Everyday" to true, "Dark" to false),
            groupsOf(draftOf(Phrase.Offered("Slice of life"), Phrase.Offered("Erotic"))),
        )
    }

    @Test
    fun `a pick in a folded group opens it, after the called-for ones`() {
        val draft = draftOf(Phrase.Offered("Slice of life")).copy(setting = Phrase.Offered("a drowned city"))

        assertEquals(listOf("Everyday" to true, "Dark" to true, "After dark" to false), groupsOf(draft))
    }

    @Test
    fun `the die rolls only from the open groups`() = runTest {
        val rolled = setting.roll(draftOf(Phrase.Offered("Slice of life"))).draft

        assertEquals(Phrase.Offered("a launderette"), rolled.setting)
    }

    @Test
    fun `the card deals from the open groups too`() = runTest {
        val you = starring.roll(draftOf(Phrase.Offered("Erotic"))).draft.protagonist!!

        assertEquals("an escort", you.role)
        assertEquals("To be wanted", you.wants)
        assertEquals("Can't say no to a dare", you.flaw)
        assertEquals("Flirting", you.goodAt)
    }

    @Test
    fun `a folded word can still be picked`() {
        val picked = setting.select(draftOf(Phrase.Offered("Slice of life")), Choice.Word("a sauna"))

        assertEquals(Phrase.Offered("a sauna"), picked?.draft?.setting)
        assertTrue(picked!!.advance)
    }
}
