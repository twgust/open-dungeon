package dev.opendungeon.domain.story

/**
 * The story's sentence as typed segments, so screens can style what was chosen without parsing text,
 * and a renamed character or a changed pick shows up everywhere at once.
 */
public data class Sentence(val segments: List<Segment>) {

    public sealed interface Segment {
        public val text: String

        /** Connecting words the app supplies: "twisted by", commas, articles. */
        public data class Fixed(override val text: String) : Segment

        /** Something picked or written; [own] marks the player's own words. */
        public data class Chosen(override val text: String, val own: Boolean) : Segment
    }

    val text: String
        get() = segments.joinToString(separator = "") { it.text }

    public fun isEmpty(): Boolean = segments.isEmpty()

    public operator fun plus(other: Sentence): Sentence = Sentence(segments + other.segments)

    public companion object {
        public val Empty: Sentence = Sentence(emptyList())
    }
}
