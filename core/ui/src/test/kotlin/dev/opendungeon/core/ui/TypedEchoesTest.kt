package dev.opendungeon.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypedEchoesTest {

    private val echoes = TypedEchoes()

    @Test
    fun `the field's own words coming back change nothing, even when they lag behind`() {
        listOf("T", "To", "To ", "To b").forEach { assertTrue(echoes.typed(it)) }

        assertNull(echoes.arrived("To", field = "To b"), "an older echo is still the field's own")
        assertNull(echoes.arrived("To b", field = "To b"))
    }

    @Test
    fun `an echo the draft skipped is not waited for`() {
        echoes.typed("A")
        echoes.typed("A b")
        echoes.typed("A be")

        assertNull(echoes.arrived("A be", field = "A be"))
        assertEquals("Lies easily", echoes.arrived("Lies easily", field = "A be"), "a later roll is still taken")
    }

    @Test
    fun `a die landing on the line being edited replaces the field`() {
        echoes.typed("To be forgiven!")

        assertEquals("To get even", echoes.arrived("To get even", field = "To be forgiven!"))
        assertFalse(echoes.typed("To get even"), "the text the draft placed is not reported back to it")
        assertTrue(echoes.typed("To get even!"), "typing after it is")
    }

    @Test
    fun `a draft that tidied the text puts its version in the field`() {
        echoes.typed(" ")

        assertEquals("", echoes.arrived("", field = " "), "blank text clears the line")
    }

    @Test
    fun `a value the field already holds needs nothing`() {
        assertNull(echoes.arrived("Picking locks", field = "Picking locks"))
    }
}
