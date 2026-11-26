package dev.opendungeon.domain.story

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterTest {

    private val maren = Character(id = CharacterId("m"), name = "Maren Kask")

    @Test
    fun `lines read and write through one door, kept as typed`() {
        val edited = maren.with(CharacterLine.Wants, "Her mother's bones back ")
        assertEquals("Her mother's bones back ", edited[CharacterLine.Wants], "a field mid-word keeps its space")
        assertNull(edited.with(CharacterLine.Wants, " ")[CharacterLine.Wants], "blank clears the line")
    }

    @Test
    fun `personality is a list typed as commas`() {
        val edited = maren.with(CharacterLine.Personality, "stubborn, dry,, secretly loyal")
        assertEquals(listOf("stubborn", "dry", "secretly loyal"), edited.personality)
        assertEquals("stubborn, dry, secretly loyal", edited[CharacterLine.Personality])
    }
}
