package dev.opendungeon.feature.newstory.domain.seeded

import kotlin.test.Test
import kotlin.test.assertEquals

class SeedFileTest {

    @Test
    fun `whole seeds are read in order`() {
        val seeds = SeedFile.seedsIn(file(whole("Your {who} sold the car."), whole("The {who} keeps knocking.")))

        assertEquals(listOf("Your aunt sold the car.", "The aunt keeps knocking."), seeds.map { it.text })
    }

    @Test
    fun `a seed that is not whole is left out, and the rest are kept`() {
        val unknownBlank = whole("Your {who} sold {thing}.")
        val unknownKind = whole("Your {who} sold the boat.").replace("\"person\"", "\"vegetable\"")
        val missingTone = whole("Your {who} sold the house.").replace("\"tone\": \"wryly\", ", "")
        val unknownShelf = whole("Your {who} sold the van.").replace("\"everyday\"", "\"cosy\"")
        val missingShelf = whole("Your {who} sold the bike.").replace("\"shelf\": \"everyday\", ", "")

        val seeds = SeedFile.seedsIn(
            file(unknownBlank, unknownKind, missingTone, unknownShelf, missingShelf, whole("Your {who} sold the car.")),
        )

        assertEquals(listOf("Your aunt sold the car."), seeds.map { it.text })
    }

    @Test
    fun `text that is not a seeds file holds no seeds`() {
        listOf("", "not json", "[]", "{}", """{"seeds": 3}""", """{"seeds": [""").forEach { text ->
            assertEquals(emptyList(), SeedFile.seedsIn(text), text)
        }
    }

    private fun file(vararg seeds: String) = """{"seeds": [${seeds.joinToString()}]}"""

    private fun whole(template: String) =
        """
        {
          "genre": "Comedy", "tone": "wryly", "shelf": "everyday", "template": "$template", "counterpart": "your {who}",
          "blanks": [{ "key": "who", "kind": "person", "fills": ["aunt", "uncle"] }]
        }
        """.trimIndent()
}
