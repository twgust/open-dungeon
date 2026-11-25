package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.feature.newstory.domain.policy.GenrePolicy
import dev.opendungeon.feature.newstory.domain.policy.TwistPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordPoliciesTest {

    private val genre = GenrePolicy(TestVocabulary, FirstWordsRoll())
    private val twist = TwistPolicy(TestVocabulary, FirstWordsRoll())

    @Test
    fun `genres toggle and stay on the step`() {
        val picked = genre.select(emptyDraft, Choice.Word("Noir"))!!
        assertEquals(listOf(Phrase.Offered("Noir")), picked.draft.genres)
        assertFalse(picked.advance)

        assertEquals(emptyList(), genre.select(picked.draft, Choice.Word("Noir"))!!.draft.genres)
    }

    @Test
    fun `genres cap at two, own words included`() {
        val two = emptyDraft.copy(genres = listOf(Phrase.Offered("Gothic"), Phrase.Own("salt-punk")))

        assertNull(genre.select(two, Choice.Word("Western")))
        assertNull(genre.submitOwn(two, "weird west"))
        assertFalse(genre.takesOwnWords(two))
    }

    @Test
    fun `own genre words are added and selected, or are the offered word they match`() {
        assertEquals(listOf(Phrase.Own("salt-punk")), genre.submitOwn(emptyDraft, "  salt-punk ")!!.draft.genres)
        assertEquals(listOf(Phrase.Offered("Gothic")), genre.submitOwn(emptyDraft, "gothic")!!.draft.genres)
        assertNull(genre.submitOwn(emptyDraft, "   "))
    }

    @Test
    fun `words the step never offered cannot be picked`() {
        assertNull(genre.select(emptyDraft, Choice.Word("Opera")))
        assertNull(twist.select(emptyDraft, Choice.Word("Gothic")))
    }

    @Test
    fun `the genre die picks two and moves on`() = runTest {
        val rolled = genre.roll(emptyDraft.copy(genres = listOf(Phrase.Offered("Gothic"))))

        assertEquals(listOf(Phrase.Offered("Noir"), Phrase.Offered("Western")), rolled.draft.genres)
        assertTrue(rolled.advance)
    }

    @Test
    fun `the step needs a genre to be left`() {
        assertFalse(genre.canAdvance(emptyDraft))
        assertTrue(genre.canAdvance(emptyDraft.copy(genres = listOf(Phrase.Own("salt-punk")))))
    }

    @Test
    fun `a single pick completes the step, and so does picking it again`() {
        val picked = twist.select(emptyDraft, Choice.Word("a curse"))!!
        assertEquals(Phrase.Offered("a curse"), picked.draft.twist)
        assertTrue(picked.advance)

        assertEquals(Selection(picked.draft, advance = true), twist.select(picked.draft, Choice.Word("a curse")))
    }

    @Test
    fun `own words complete a single step`() {
        val own = twist.submitOwn(emptyDraft, "a stolen saint")!!

        assertEquals(Phrase.Own("a stolen saint"), own.draft.twist)
        assertTrue(own.advance)
    }

    @Test
    fun `the single step die changes the pick and moves on`() = runTest {
        val rolled = twist.roll(emptyDraft.copy(twist = Phrase.Offered("a heist")))

        assertEquals(Phrase.Offered("a curse"), rolled.draft.twist)
        assertTrue(rolled.advance)
    }

    @Test
    fun `own words are shown first and picked`() {
        val body = twist.body(emptyDraft.copy(twist = Phrase.Own("a stolen saint"))) as StepBody.Words

        assertEquals(Option(Choice.Word("a stolen saint"), "a stolen saint", picked = true, own = true), body.options.first())
        assertEquals(1, body.options.count(Option::picked))
        assertEquals(1, body.maxPicks)
    }
}
