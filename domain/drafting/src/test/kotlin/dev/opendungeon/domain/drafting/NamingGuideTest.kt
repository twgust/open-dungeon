package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NamingGuideTest {

    private val guide = BundledNamingGuide()

    private fun notesFor(vararg genres: Phrase): List<String> =
        guide.of(StoryDraft(id = DraftId("d"), genres = genres.toList()))

    @Test
    fun `a slice of life names people the way everyday life does`() {
        val notes = notesFor(Phrase.Offered("Slice of life"))

        assertEquals(BundledNames.file.always, notes.first())
        assertEquals(2, notes.size)
        assertTrue(notes[1].startsWith("Slice of life: Ordinary names"), notes[1])
        assertTrue("Lisa" in notes[1])
    }

    @Test
    fun `the longest genre phrase wins, so cosmic horror is not plain horror`() {
        val naming = GenreNaming(BundledNames.file.always, BundledNames.file.styles)

        assertEquals(listOf("cosmic horror", "lovecraftian"), naming.styleOf("Cosmic horror")?.genres)
        assertEquals(listOf("southern gothic"), naming.styleOf("Southern gothic")?.genres)
        assertEquals(listOf("horror", "survival horror", "slasher"), naming.styleOf("Horror")?.genres)
    }

    @Test
    fun `the player's own words find a style when they name a genre the guide knows`() {
        val notes = notesFor(Phrase.Own("cosy slice of life with ghosts"))

        assertTrue(notes[1].startsWith("cosy slice of life with ghosts: Ordinary names"), notes[1])
    }

    @Test
    fun `a genre the guide does not know leaves only the rule every story follows`() {
        assertEquals(listOf(BundledNames.file.always), notesFor(Phrase.Own("salt-punk")))
    }

    @Test
    fun `a phrase matches only as whole words`() {
        val naming = GenreNaming(BundledNames.file.always, BundledNames.file.styles)

        assertEquals(null, naming.styleOf("salsa"))
        assertEquals(null, naming.styleOf("mythical beasts"))
    }

    @Test
    fun `two genres of one style share a line, and two of different styles get one each`() {
        assertEquals(2, notesFor(Phrase.Offered("Comedy"), Phrase.Offered("Romance")).size)
        assertTrue(notesFor(Phrase.Offered("Comedy"), Phrase.Offered("Romance"))[1].startsWith("Comedy and Romance: "))
        assertEquals(3, notesFor(Phrase.Offered("Noir"), Phrase.Offered("Space opera")).size)
    }

    @Test
    fun `phrases are lower case and belong to one style`() {
        assertFailsWith<IllegalArgumentException> { GenreNaming("x", listOf(NameStyle(listOf("Noir"), "y"))) }
        assertFailsWith<IllegalArgumentException> {
            GenreNaming("x", listOf(NameStyle(listOf("noir"), "y"), NameStyle(listOf("noir"), "z")))
        }
        GenreNaming(BundledNames.file.always, BundledNames.file.styles)
    }

    @Test
    fun `every style is short enough for a prompt line`() {
        val long = BundledNames.file.styles.filter { it.names.split(' ').size > MAX_WORDS }.map { it.genres.first() }

        assertEquals(emptyList(), long)
    }

    private companion object {
        const val MAX_WORDS = 40
    }
}
