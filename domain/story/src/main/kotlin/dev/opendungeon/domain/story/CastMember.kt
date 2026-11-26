package dev.opendungeon.domain.story

/** Someone offered for the story, or written by the player, and whether they are in it. */
public data class CastMember(val character: Character, val origin: Origin, val inStory: Boolean) {
    public enum class Origin { Offered, Own }
}
