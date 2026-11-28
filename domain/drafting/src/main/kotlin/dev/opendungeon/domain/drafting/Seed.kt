package dev.opendungeon.domain.drafting

/**
 * A premise to play straight away: one sentence with blanks, such as "Caught {act} by your {who}.", each
 * blank filled with words of its kind and holding more words that would fit it.
 *
 * A seed is always whole: every blank in [template] has exactly one [Blank], every blank is used, and
 * nothing is empty. [of] checks a seed that came from a model; the constructor rejects one that is not
 * whole, since only a bug could build it.
 */
public data class Seed(
    /** What kind of story it is, in a word or two: "Comedy". */
    val genre: String,
    /** How it is told, the way story creation's tones say it: "wryly". */
    val tone: String,
    /** The sentence, with each blank written as its key in braces. */
    val template: String,
    /** The person the player meets first, in the same form: "your {who}", or "the estate agent". */
    val counterpart: String,
    val blanks: List<Blank>,
) {
    init {
        val problem = problemOf(genre, tone, template, counterpart, blanks)
        require(problem == null) { "Not a whole seed: $problem" }
    }

    /** One blank: the words in it now, those shown before, and those still to come. */
    public data class Blank(
        /** Its name in the template, in lower case letters. */
        val key: String,
        val kind: Kind,
        val fill: String,
        /** Words that fit and have not been shown, in the order they will be. */
        val unused: List<String> = emptyList(),
        /** Words shown before [fill], oldest first. */
        val used: List<String> = emptyList(),
    ) {
        /** Every word this blank has shown, the current one last. */
        val shown: List<String> get() = used + fill

        /** The next unused words in place of [fill], or `null` when there are none left. */
        public fun next(): Blank? {
            val coming = unused.firstOrNull() ?: return null
            return copy(fill = coming, unused = unused.drop(1), used = used + fill)
        }

        /** [fills] added to the words to come, leaving out blank ones and any this blank already has. */
        public fun withMore(fills: List<String>): Blank {
            val known = (shown + unused).mapTo(HashSet()) { it.key() }
            val fresh = fills.map(String::trim).filter { it.isFillable() && known.add(it.key()) }
            return copy(unused = unused + fresh)
        }
    }

    /** What a blank asks for. It tells a model what to write in it, and a screen reader what it is. */
    public enum class Kind { Person, Thing, Act, Place }

    /** A piece of the sentence as it reads now. */
    public sealed interface Part {
        public val text: String

        public data class Text(override val text: String) : Part

        /** The words in the blank [key], capitalised when they open the sentence. */
        public data class Filled(val key: String, val kind: Kind, override val text: String) : Part
    }

    /** The sentence as it reads now, in pieces, so a screen can set the blanks apart. */
    val parts: List<Part> get() = partsOf(template)

    /** The sentence as it reads now. */
    val text: String get() = parts.joinToString(separator = "") { it.text }

    /** The person the player meets first, as the sentence names them now: "your grandmother". */
    val counterpartText: String get() = partsOf(counterpart, capitalise = false).joinToString("") { it.text }

    public fun blank(key: String): Blank? = blanks.firstOrNull { it.key == key }

    /** This seed with [blank] in place of the blank with the same key. */
    public fun with(blank: Blank): Seed {
        require(blank(blank.key)?.kind == blank.kind) { "No ${blank.kind} blank called ${blank.key}" }
        return copy(blanks = blanks.map { if (it.key == blank.key) blank else it })
    }

    private fun partsOf(text: String, capitalise: Boolean = true): List<Part> {
        val parts = mutableListOf<Part>()
        var at = 0
        PLACEHOLDER.findAll(text).forEach { match ->
            if (match.range.first > at) parts += Part.Text(text.substring(at, match.range.first))
            val blank = blanks.first { it.key == match.groupValues[1] }
            val words = if (capitalise && match.range.first == 0) blank.fill.capitalised() else blank.fill
            parts += Part.Filled(blank.key, blank.kind, words)
            at = match.range.last + 1
        }
        if (at < text.length) parts += Part.Text(text.substring(at))
        return parts
    }

    public companion object {
        public const val MAX_TEMPLATE_LENGTH: Int = 160
        public const val MAX_FILL_LENGTH: Int = 60
        public const val MAX_LABEL_LENGTH: Int = 40

        /**
         * A seed of these parts, trimmed, or `null` when they do not make a whole one. Words in [blanks]
         * after the first are the blank's unused words; blank and repeated ones are dropped first.
         */
        public fun of(
            genre: String,
            tone: String,
            template: String,
            counterpart: String,
            blanks: List<Triple<String, Kind, List<String>>>,
        ): Seed? {
            val made = blanks.map { (key, kind, fills) ->
                val words = fills.map(String::trim).filter(String::isFillable).distinctBy { it.key() }
                val first = words.firstOrNull() ?: return null
                Blank(key.trim(), kind, first, unused = words.drop(1))
            }
            val kind = genre.trim()
            val telling = tone.trim()
            val sentence = template.trim()
            val other = counterpart.trim()
            if (problemOf(kind, telling, sentence, other, made) != null) return null
            return Seed(kind, telling, sentence, other, made)
        }

        private val PLACEHOLDER = Regex("""\{([a-z]+)\}""")
        private val KEY = Regex("""[a-z]+""")

        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        private fun problemOf(
            genre: String,
            tone: String,
            template: String,
            counterpart: String,
            blanks: List<Blank>,
        ): String? {
            if (genre.isBlank() || genre.length > MAX_LABEL_LENGTH) return "genre"
            if (tone.isBlank() || tone.length > MAX_LABEL_LENGTH) return "tone"
            if (template.isBlank() || template.length > MAX_TEMPLATE_LENGTH) return "template length"
            if (counterpart.isBlank() || counterpart.length > MAX_TEMPLATE_LENGTH) return "counterpart length"
            val keys = blanks.map(Blank::key)
            if (keys.isEmpty() || keys.size != keys.toSet().size || keys.any { !KEY.matches(it) }) return "keys"
            val inTemplate = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()
            if (inTemplate.size != inTemplate.toSet().size ||
                inTemplate.toSet() != keys.toSet()
            ) {
                return "template blanks"
            }
            if (template.withoutPlaceholders().isStray() || counterpart.withoutPlaceholders().isStray()) return "braces"
            if (PLACEHOLDER.findAll(counterpart).any { it.groupValues[1] !in keys }) return "counterpart blanks"
            if (blanks.any { blank -> (blank.shown + blank.unused).any { !it.isFillable() } }) return "fills"
            return null
        }

        private fun String.withoutPlaceholders(): String = PLACEHOLDER.replace(this, "")

        private fun String.isStray(): Boolean = '{' in this || '}' in this
    }
}

/** Words that can stand in a blank: something to read, short, and no braces to confuse the template. */
private fun String.isFillable(): Boolean =
    isNotBlank() && length <= Seed.MAX_FILL_LENGTH && '{' !in this && '}' !in this && '\n' !in this

/** Words compared as a reader would: "A goose" and "a goose " are the same. */
private fun String.key(): String = trim().lowercase()

private fun String.capitalised(): String = replaceFirstChar { it.titlecase() }
