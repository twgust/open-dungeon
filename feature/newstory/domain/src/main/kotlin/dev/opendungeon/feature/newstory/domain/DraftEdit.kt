package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CharacterLine

/** Text the player types into the current step, as opposed to a pill they tap. */
public sealed interface DraftEdit {
    public val text: String

    /** One line of the player's own character card. */
    public data class YouLine(val line: CharacterLine, override val text: String) : DraftEdit

    /** The player's own limits, passed to the model in their words. */
    public data class LimitsNote(override val text: String) : DraftEdit

    public data class Premise(override val text: String) : DraftEdit

    public data class FirstLine(override val text: String) : DraftEdit
}
