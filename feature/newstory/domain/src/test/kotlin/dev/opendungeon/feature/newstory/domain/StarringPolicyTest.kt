package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.feature.newstory.domain.policy.StarringPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    fun `the card shows its three lines in order, a cleared one left blank to refill`() {
        val dealt = policy.select(emptyDraft, Choice.Word("a forger"))!!.draft
        val cleared = policy.edit(dealt, DraftEdit.YouLine(CharacterLine.Flaw, " "))!!

        val body = assertIs<StepBody.Starring>(policy.body(cleared))

        assertEquals(
            listOf(
                ShownLine(CharacterLine.Wants, "To be forgiven"),
                ShownLine(CharacterLine.Flaw, ""),
                ShownLine(CharacterLine.GoodAt, "Picking locks"),
            ),
            body.you!!.lines,
        )
        assertNull(assertIs<StepBody.Starring>(policy.body(emptyDraft)).you, "no card before a role is picked")
    }

    @Test
    fun `there is nothing to edit or advance before a role is picked`() {
        assertNull(policy.edit(emptyDraft, DraftEdit.YouLine(CharacterLine.Wants, "x")))
        assertFalse(policy.canAdvance(emptyDraft))
    }

    @Test
    fun `the player's own role deals a card and leads the roles, in its own group`() {
        assertTrue(policy.takesOwnWords(emptyDraft))

        val picked = policy.submitOwn(emptyDraft, "  a deserter who bakes bread ")!!
        val you = picked.draft.protagonist!!

        assertEquals("a deserter who bakes bread", you.role)
        assertEquals("To be forgiven", you.wants)
        assertFalse(picked.advance)
        val first = assertIs<StepBody.Starring>(policy.body(picked.draft)).roles.first()
        assertEquals(
            OptionGroup(
                null,
                listOf(
                    Option(
                        Choice.Word("a deserter who bakes bread"),
                        "a deserter who bakes bread",
                        picked = true,
                        own = true,
                    ),
                ),
                open = true,
            ),
            first,
        )
    }

    @Test
    fun `an own role replaces an offered one and keeps the card`() {
        val dealt = policy.select(emptyDraft, Choice.Word("a forger"))!!.draft

        val own = policy.submitOwn(dealt, "a nun on the run")!!.draft.protagonist!!

        assertEquals(dealt.protagonist!!.copy(role = "a nun on the run"), own)
    }

    @Test
    fun `typing an offered role is picking it`() {
        val draft = policy.submitOwn(emptyDraft, "A FORGER")!!.draft

        assertEquals("a forger", draft.protagonist!!.role)
        val body = assertIs<StepBody.Starring>(policy.body(draft))
        assertTrue(body.roles.none { it.label == null }, "an offered role is not shown as the player's own")
    }

    @Test
    fun `blank words are no role`() {
        assertNull(policy.submitOwn(emptyDraft, "   "))
    }

    @Test
    fun `tapping the player's own role again keeps it`() {
        val draft = policy.submitOwn(emptyDraft, "a deserter")!!.draft

        assertEquals(draft, policy.select(draft, Choice.Word("a deserter"))!!.draft)
    }
}
