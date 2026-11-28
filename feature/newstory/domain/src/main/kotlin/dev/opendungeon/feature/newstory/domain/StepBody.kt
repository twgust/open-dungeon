package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.ContentLimits

/** What the current step shows under the sentence, already decided by its policy. Screens only render it. */
public sealed interface StepBody {

    /** Word pills in groups: genre, twist, tone or setting. */
    public data class Words(val groups: List<OptionGroup>, val maxPicks: Int) : StepBody

    /** Role pills in groups, then the player's card once a role is picked. */
    public data class Starring(val roles: List<OptionGroup>, val you: YouCard?) : StepBody

    public data class Limits(val limits: ContentLimits) : StepBody

    /** The premise, written by the player; [own] tells whether they wrote it. */
    public data class Premise(val text: String, val own: Boolean) : StepBody

    public data class Cast(val people: List<PersonCard>) : StepBody

    /**
     * How the story begins: one text the player writes, and a model can write or carry on.
     *
     * [text] is what the field holds. While a model writes, it is the text the writing started from,
     * and [writing] is what the model is adding after it, which is not part of the text until it ends.
     */
    public data class Opening(
        val text: String,
        val writing: Writing?,
        /** What the wand does with the text as it is. */
        val wand: Wand,
        /** The model's last addition, still in the text and still possible to take back; `null` when there is none. */
        val addition: Addition?,
        /** The last writing failed, and [text] is exactly what it was before. */
        val failed: Boolean,
        val maxLength: Int,
    ) : StepBody {

        public sealed interface Writing {
            /** Asked, with no words back yet. */
            public data object Waiting : Writing

            /** The words so far, led by whatever space joins them to [text]. */
            public data class Adding(val text: String) : Writing
        }

        public enum class Wand {
            /** The field is empty: the model writes the whole opening. */
            WriteForMe,

            /** The field has text: the model carries on after it, never changing what is there. */
            ContinueMyText,
        }

        /** [stopped] when the player cut the writing short and kept what it had written. */
        public data class Addition(val stopped: Boolean)
    }
}
