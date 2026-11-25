package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.Take
import dev.opendungeon.domain.story.TakeId

/** What the current step shows under the sentence. */
public sealed interface StepBody {

    /** A row of word pills: genre, twist, tone or setting. */
    public data class Words(val options: List<Option>, val maxPicks: Int) : StepBody

    /** Role pills, then the player's card once a role is picked. */
    public data class Starring(val roles: List<Option>, val you: Character?) : StepBody {
        /** The lines the card shows, each with its own die. */
        val lines: List<CharacterLine> get() = YOU_LINES

        public companion object {
            public val YOU_LINES: List<CharacterLine> = listOf(CharacterLine.Wants, CharacterLine.Flaw, CharacterLine.GoodAt)
        }
    }

    public data class Limits(val limits: ContentLimits) : StepBody

    /** The premise, written by the player; [own] tells whether they wrote it. */
    public data class Premise(val text: String, val own: Boolean) : StepBody

    public data class Cast(val members: List<CastMember>) : StepBody

    public data class FirstLine(val text: String, val maxLength: Int) : StepBody

    public data class Auditions(val takes: List<Take>, val chosen: TakeId?) : StepBody
}
