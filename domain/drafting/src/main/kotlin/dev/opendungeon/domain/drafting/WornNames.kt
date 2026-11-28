package dev.opendungeon.domain.drafting

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.text.Normalizer

/**
 * The names models reach for when nobody stops them: Kael, Elara, Lyra, Thorne, Voss. One of them on a
 * card marks the whole story as machine-written, so no generated person keeps one.
 */
public interface WornNames {
    /**
     * The worn names in [text], each as the list spells it, or an empty set when it has none. Case,
     * accents and apostrophes do not hide a name: "Kaël" and "Ka'el" are both Kael.
     */
    public fun foundIn(text: String): Set<String>
}

/** The list bundled with this module, from `prompts/names.json`. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BundledWornNames : WornNames {

    private val list by lazy { WornNameList(BundledNames.file.worn) }

    override fun foundIn(text: String): Set<String> = list.foundIn(text)
}

/**
 * Finds the entries of a worn-name list in a text. An entry is one of three kinds:
 * - a word, such as `elara`, which matches that word anywhere in a name;
 * - a stem, such as `kael*`, which matches any word starting with it: Kael, Kaelen, Kaelith;
 * - a full name, such as `sarah chen`, which matches only those words in that order, since Sarah and Chen
 *   are each fine on their own.
 */
internal class WornNameList(entries: List<String>) {

    private val entries: List<Entry> = entries.map(::entry)

    fun foundIn(text: String): Set<String> {
        val words = words(text)
        if (words.isEmpty()) return emptySet()
        return entries.filter { it.matches(words) }.mapTo(LinkedHashSet()) { it.spelling }
    }

    private sealed interface Entry {
        val spelling: String

        fun matches(words: List<String>): Boolean
    }

    private class Word(override val spelling: String) : Entry {
        override fun matches(words: List<String>): Boolean = spelling in words
    }

    private class Stem(override val spelling: String, private val stem: String) : Entry {
        override fun matches(words: List<String>): Boolean = words.any { it.startsWith(stem) }
    }

    private class FullName(override val spelling: String, private val parts: List<String>) : Entry {
        override fun matches(words: List<String>): Boolean = words.windowed(parts.size).any { it == parts }
    }

    companion object {
        private const val STAR = '*'
        private const val MIN_STEM = 3
        private val APOSTROPHES = Regex("""['’ʼ`]""")
        private val MARKS = Regex("""\p{M}+""")
        private val NOT_LETTERS = Regex("""[^\p{L}]+""")

        private fun entry(spelling: String): Entry {
            val parts = words(spelling.removeSuffix(STAR.toString()))
            require(parts.isNotEmpty() && parts.joinToString(" ") == spelling.removeSuffix(STAR.toString())) {
                "A worn name is written in plain lower case letters: \"$spelling\""
            }
            return when {
                spelling.endsWith(STAR) -> {
                    require(parts.size == 1 && parts.single().length >= MIN_STEM) {
                        "A stem is one word of at least $MIN_STEM letters: \"$spelling\""
                    }
                    Stem(spelling, parts.single())
                }
                parts.size == 1 -> Word(spelling)
                else -> FullName(spelling, parts)
            }
        }

        /** [text] as plain lower case words: accents and apostrophes dropped, anything not a letter a break. */
        fun words(text: String): List<String> = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(APOSTROPHES, "")
            .lowercase()
            .split(NOT_LETTERS)
            .filter(String::isNotEmpty)
    }
}
