package dev.opendungeon.domain.drafting

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keeps the bundled dialogue styles usable in a prompt: short tags, short descriptions. */
class DialogueStylesTest {

    private val styles: Map<String, String> by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream(RESOURCE)) { "Missing $RESOURCE" }
            .bufferedReader()
            .use { it.readText() }
        Json.decodeFromString<JsonObject>(text).mapValues { it.value.jsonPrimitive.content }
    }

    @Test
    fun `every key is a lower case tag`() {
        assertEquals(emptyList(), styles.keys.filterNot { KEY.matches(it) })
    }

    @Test
    fun `every description is at most twelve words`() {
        val long = styles.filterValues { it.split(Regex("""\s+""")).size > MAX_WORDS }
        assertEquals(emptyMap(), long)
    }

    @Test
    fun `there are enough styles to choose from`() {
        assertTrue(styles.size >= MIN_STYLES, "only ${styles.size} styles")
    }

    private companion object {
        const val RESOURCE = "/dev/opendungeon/domain/drafting/prompts/dialogue-styles.json"
        const val MAX_WORDS = 12
        const val MIN_STYLES = 20
        val KEY = Regex("""[a-z]+(-[a-z]+)*""")
    }
}
