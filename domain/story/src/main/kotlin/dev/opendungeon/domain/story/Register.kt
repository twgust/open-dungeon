package dev.opendungeon.domain.story

/**
 * The kind of life a story is about: ordinary days, adult ones, dark ones, or lives in other worlds and
 * times. Genres, the words story creation offers and the seeds it deals each belong to some, which is how
 * a slice of life is offered a night-shift nurse before a grave robber.
 */
public enum class Register(
    /** Offered only to a story that asks for it by its genre or shelf, never by default or by chance. */
    public val optIn: Boolean = false,
) {
    Everyday,
    AfterDark(optIn = true),
    Dark,
    Fantastical,
    OtherWorlds,
    Historical,
}
