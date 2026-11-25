package dev.opendungeon.domain.story

/** A choice in the story's sentence: one the app offered, or the player's own words. */
public sealed interface Phrase {
    public val text: String

    public data class Offered(override val text: String) : Phrase

    public data class Own(override val text: String) : Phrase {
        init {
            require(text.isNotBlank()) { "Own words cannot be blank" }
        }
    }
}
