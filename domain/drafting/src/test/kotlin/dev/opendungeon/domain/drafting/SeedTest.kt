package dev.opendungeon.domain.drafting

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SeedTest {

    private val caught = assertNotNull(
        Seed.of(
            genre = "Comedy",
            tone = "playfully",
            template = "{act} got you caught by your {who}.",
            counterpart = "your {who}",
            blanks = listOf(
                Triple("act", Seed.Kind.Act, listOf("forging a note", " forging a NOTE ", "", "lying")),
                Triple("who", Seed.Kind.Person, listOf("grandmother", "boss")),
            ),
        ),
    )

    @Test
    fun `the sentence reads with its blanks filled, capitalised where a blank opens it`() {
        assertEquals("Forging a note got you caught by your grandmother.", caught.text)
        assertEquals(
            listOf(
                Seed.Part.Filled("act", Seed.Kind.Act, "Forging a note"),
                Seed.Part.Text(" got you caught by your "),
                Seed.Part.Filled("who", Seed.Kind.Person, "grandmother"),
                Seed.Part.Text("."),
            ),
            caught.parts,
        )
    }

    @Test
    fun `the counterpart follows the blank it names and is never capitalised`() {
        assertEquals("your grandmother", caught.counterpartText)
        val boss = caught.with(assertNotNull(caught.blank("who")?.next()))
        assertEquals("your boss", boss.counterpartText)
    }

    @Test
    fun `blank and repeated words are dropped, the first word is the fill and the rest wait`() {
        val act = assertNotNull(caught.blank("act"))
        assertEquals("forging a note", act.fill)
        assertEquals(listOf("lying"), act.unused)
        assertEquals(emptyList(), act.used)
    }

    @Test
    fun `next shows the words that wait in turn, then runs out`() {
        val act = assertNotNull(caught.blank("act"))
        val second = assertNotNull(act.next())

        assertEquals("lying", second.fill)
        assertEquals(listOf("forging a note", "lying"), second.shown)
        assertNull(second.next())
    }

    @Test
    fun `more words leave out what the blank already has, blank words and words too long or with braces`() {
        val act = assertNotNull(caught.blank("act"))

        val more = act.withMore(
            listOf("Lying", "forging a note", " ", "x".repeat(Seed.MAX_FILL_LENGTH + 1), "{x}", "  bribing a judge "),
        )

        assertEquals(listOf("lying", "bribing a judge"), more.unused)
    }

    @Test
    fun `a seed is not whole when a blank is missing, unused, repeated, or has no words`() {
        fun of(
            template: String,
            counterpart: String = "the judge",
            keys: List<String> = listOf("who"),
            fills: List<String> = listOf("a"),
        ) = Seed.of("Drama", "slowly", template, counterpart, keys.map { Triple(it, Seed.Kind.Person, fills) })

        assertNotNull(of("Judged by {who}."))
        assertNull(of("Judged by {who} and {what}."), "a blank without words")
        assertNull(of("Judged by nobody."), "a blank the template never uses")
        assertNull(of("Judged by {who} and {who}."), "a blank used twice")
        assertNull(of("Judged by {who}.", keys = listOf("who", "who")), "a key given twice")
        assertNull(of("Judged by {who}.", fills = listOf(" ")), "no words to fill it with")
        assertNull(of("Judged by {who}.", counterpart = "your {boss}"), "a counterpart naming no blank")
        assertNull(of("Judged by {Who}.", keys = listOf("Who")), "keys are lower case")
        assertNull(of("Judged by {who} {"), "a stray brace")
        assertNull(of("Judged by {who}." + "x".repeat(Seed.MAX_TEMPLATE_LENGTH)), "too long")
    }

    @Test
    fun `building a seed that is not whole is a bug`() {
        assertThrows<IllegalArgumentException> {
            Seed("Drama", "slowly", "Judged by {who}.", "the judge", emptyList())
        }
    }

    @Test
    fun `a blank is only replaced by one of its own kind`() {
        assertThrows<IllegalArgumentException> {
            caught.with(Seed.Blank("who", Seed.Kind.Place, "a pub"))
        }
    }
}
