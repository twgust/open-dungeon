package dev.opendungeon.domain.drafting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WornNamesTest {

    private val worn = BundledWornNames()

    @Test
    fun `the names a model reaches for are found, in any spelling`() {
        assertEquals(setOf("kael*", "voss"), worn.foundIn("Kaelen Voss"))
        assertEquals(setOf("elara", "thorne"), worn.foundIn("Dr. Elara Thorne"))
        assertEquals(setOf("kael*"), worn.foundIn("Kaël"))
        assertEquals(setOf("kael*"), worn.foundIn("Ka'el"))
        assertEquals(setOf("lyra"), worn.foundIn("LYRA"))
        assertEquals(setOf("mara", "valen"), worn.foundIn("Mara and Valen"))
    }

    @Test
    fun `a full name is worn only whole`() {
        assertEquals(setOf("sarah chen"), worn.foundIn("Sarah Chen"))
        assertEquals(emptySet(), worn.foundIn("Sarah Lindqvist"))
        assertEquals(emptySet(), worn.foundIn("Chen Wei"))
    }

    @Test
    fun `everyday names from many places are not worn`() {
        val flagged = EVERYDAY.associateWith(worn::foundIn).filterValues(Set<String>::isNotEmpty)

        assertEquals(emptyMap(), flagged)
    }

    @Test
    fun `no genre's example names are worn`() {
        val flagged = BundledNames.file.styles.associate { it.genres.first() to worn.foundIn(it.names) }
            .filterValues(Set<String>::isNotEmpty)

        assertEquals(emptyMap(), flagged)
    }

    @Test
    fun `no example in a schema is worn`() {
        val examples = CastPayload.EXAMPLE.people.map { it.name } +
            SeedCastPayload.EXAMPLE.you.name +
            RenamesPayload.EXAMPLE.names.map { it.name }

        assertTrue(examples.all { worn.foundIn(it).isEmpty() }, "$examples")
    }

    @Test
    fun `the bundled list has no repeats`() {
        val list = BundledNames.file.worn

        assertEquals(list.size, list.toSet().size)
    }

    @Test
    fun `an entry is plain lower case letters, and a stem is long enough to mean something`() {
        assertFailsWith<IllegalArgumentException> { WornNameList(listOf("Kael")) }
        assertFailsWith<IllegalArgumentException> { WornNameList(listOf("ka'el")) }
        assertFailsWith<IllegalArgumentException> { WornNameList(listOf("ka*")) }
        assertFailsWith<IllegalArgumentException> { WornNameList(listOf("sarah chen*")) }
    }

    @Test
    fun `text with no letters has no worn names`() {
        assertEquals(emptySet(), worn.foundIn(" — 42 "))
    }

    private companion object {
        /** Common given names and surnames across languages, which a stem must never catch. */
        val EVERYDAY: List<String> =
            """
            Lisa, Emma, Jonas, Dave, Michael, Valentina, Valentin, Marie, Maren Kask, Noor Haddad
            Marco Rossi, Ross Geller, Martha, Thomas, Caroline, Kai, Mark, Lily, Laura, Elena, Elias, Marcus
            Sarah, Ellie, Vanessa, Kelly, Ashley, Ava, Liam, Olivia, Noah, Sofia, Lukas, Anna, Sven, Ingrid
            Astrid, Björn, Sanna, Mehmet Aydin, Ayşe, Yuki Tanaka, Haruto, Mei, Zhao Wen, Priya, Arjun
            Fatima, Omar, Chidi Okafor, Nkechi, Juan, Lupe Ortega, José, Siobhán, Seán, Dmitri, Olga
            Kasia Nowak, Thornton, Vasquez, Blackburn, Ashworth, Embers, Valerie, Vera, Lyndon, Corbin
            """.trimIndent().split(Regex("""\s*[,\n]\s*"""))
    }
}
