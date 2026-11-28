package dev.opendungeon.domain.drafting

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The naming rules bundled with this module, read once. They are data, not code, so a genre's names or a
 * worn name can be added without touching a rule.
 */
internal object BundledNames {
    private const val RESOURCE = "/dev/opendungeon/domain/drafting/prompts/names.json"

    val file: NamesFile by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream(RESOURCE)) { "Missing $RESOURCE" }
            .bufferedReader()
            .use { it.readText() }
        Json.decodeFromString<NamesFile>(text)
    }
}

/** `names.json`: how every story names people, how each genre does, and the names no story uses. */
@Serializable
internal class NamesFile(val always: String, val styles: List<NameStyle>, val worn: List<String>)

/** How the stories of some genres name people. [genres] are lower case, and the longest match wins. */
@Serializable
internal class NameStyle(val genres: List<String>, val names: String)
