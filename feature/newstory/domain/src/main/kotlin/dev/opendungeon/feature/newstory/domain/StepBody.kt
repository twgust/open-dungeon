package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.ContentLimits

/** What the current step shows under the sentence, already decided by its policy. Screens only render it. */
public sealed interface StepBody {

    /** A row of word pills: genre, twist, tone or setting. */
    public data class Words(val options: List<Option>, val maxPicks: Int) : StepBody

    /** Role pills, then the player's card once a role is picked. */
    public data class Starring(val roles: List<Option>, val you: YouCard?) : StepBody

    public data class Limits(val limits: ContentLimits) : StepBody

    /** The premise, written by the player; [own] tells whether they wrote it. */
    public data class Premise(val text: String, val own: Boolean) : StepBody

    public data class Cast(val people: List<PersonCard>) : StepBody

    public data class FirstLine(val text: String, val maxLength: Int) : StepBody

    public data class Auditions(val auditions: List<Audition>) : StepBody
}
