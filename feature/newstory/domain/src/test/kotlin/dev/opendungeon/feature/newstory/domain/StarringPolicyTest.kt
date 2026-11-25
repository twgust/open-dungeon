package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.feature.newstory.domain.policy.StarringPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarringPolicyTest {

    private val policy = StarringPolicy(TestVocabulary, FirstWordsRoll(), CountingIds("you"))

    @Test
    fun `picking a role deals a full card and waits for the next arrow`() {
        val picked = policy.select(emptyDraft, Choice.Word("a forger"))!!
        val you = picked.draft.protagonist!!

        assertEquals("a forger", you.role)
        assertEquals("To be forgiven", you.wants)
        assertEquals("Lies easily", you.flaw)
        assertEquals("Picking locks", you.goodAt)
        assertFalse(picked.advance)
        assertTrue(policy.canAdvance(picked.draft))
    }

    @Test
    fun `picking another role keeps the card`() {
        val first = policy.select(emptyDraft, Choice.Word("a forger"))!!.draft
        val edited = policy.edit(first, DraftEdit.YouLine(CharacterLine.Wants, "To find her sister"))!!

        val second = policy.select(edited, Choice.Word("a ferry pilot"))!!.draft.protagonist!!

        assertEquals("a ferry pilot", second.role)
        assertEquals("To find her sister", second.wants)
    }

    @Test
    fun `the die redeals the role and all three lines`() = runTest {
        val dealt = policy.select(emptyDraft, Choice.Word("a disgraced smuggler"))!!.draft

        val rerolled = policy.roll(dealt).draft.protagonist!!

        val before = dealt.protagonist!!
        assertNotEquals(before.role, rerolled.role)
        assertNotEquals(before.wants, rerolled.wants)
        assertNotEquals(before.flaw, rerolled.flaw)
        assertNotEquals(before.goodAt, rerolled.goodAt)
        assertEquals(before.id, rerolled.id)
    }

    @Test
    fun `a line's die changes only that line`() = runTest {
        val dealt = policy.select(emptyDraft, Choice.Word("a forger"))!!.draft

        val you = policy.rollLine(dealt, CharacterLine.Flaw)!!.protagonist!!

        assertEquals("Keeps every grudge", you.flaw)
        assertEquals(dealt.protagonist!!.copy(flaw = "Keeps every grudge"), you)
        assertNull(policy.rollLine(dealt, CharacterLine.Name), "only the card's lines have dice")
    }

    @Test
    fun `there is nothing to edit or advance before a role is picked`() {
        assertNull(policy.edit(emptyDraft, DraftEdit.YouLine(CharacterLine.Wants, "x")))
        assertFalse(policy.canAdvance(emptyDraft))
    }
}
