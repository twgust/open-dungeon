package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryChoices
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * How the people of a story are named, so a slice of life gets a Lisa and a wuxia a Zhao Wen rather than
 * every story getting the same invented fantasy names.
 */
public interface NamingGuide {
    /**
     * The naming notes for [story], one per line: the rule every story follows, then one for each of its
     * genres the guide knows, in the order the player chose them. A genre the guide does not know adds
     * nothing; the first rule still ties names to the setting.
     */
    public fun of(story: StoryChoices): List<String>
}

/** The guide bundled with this module, from `prompts/names.json`. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BundledNamingGuide : NamingGuide {

    private val guide by lazy { GenreNaming(BundledNames.file.always, BundledNames.file.styles) }

    override fun of(story: StoryChoices): List<String> = guide.of(story.genres.map(Phrase::text))
}

/** Matches genres, offered or in the player's own words, to the naming style that fits them. */
internal class GenreNaming(private val always: String, styles: List<NameStyle>) {

    /** Every genre phrase with its style and a pattern that finds it as whole words, longest first. */
    private val phrases: List<Pair<Regex, NameStyle>> = styles
        .flatMap { style -> style.genres.map { it to style } }
        .sortedByDescending { (phrase, _) -> phrase.length }
        .map { (phrase, style) -> Regex("""(?<![\p{L}\p{N}])${Regex.escape(phrase)}(?![\p{L}\p{N}])""") to style }

    init {
        val all = styles.flatMap(NameStyle::genres)
        require(all.all { it == it.lowercase() && it.isNotBlank() }) { "Genre phrases are lower case words" }
        require(all.size == all.toSet().size) { "A genre phrase belongs to one style" }
    }

    fun of(genres: List<String>): List<String> {
        val matched = LinkedHashMap<NameStyle, MutableList<String>>()
        genres.forEach { genre -> styleOf(genre)?.let { matched.getOrPut(it, ::mutableListOf) += genre.trim() } }
        return listOf(always) + matched.map { (style, named) -> "${named.joinToString(" and ")}: ${style.names}" }
    }

    /** The style whose longest phrase appears in [genre], so "cosmic horror" is not read as plain horror. */
    fun styleOf(genre: String): NameStyle? {
        val text = genre.lowercase()
        return phrases.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
    }
}

/** [NamingGuide.of] as a prompt section, under [heading]. */
internal fun NamingGuide.notes(story: StoryChoices, heading: String = NAMES): String = buildString {
    append(heading)
    of(story).forEach { append("\n- $it") }
}

private const val NAMES = "Names:"
