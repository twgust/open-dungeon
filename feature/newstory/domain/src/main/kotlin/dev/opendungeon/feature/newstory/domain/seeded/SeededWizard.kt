package dev.opendungeon.feature.newstory.domain.seeded

import dev.opendungeon.domain.drafting.Seed
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.LeaveAnswer
import dev.opendungeon.feature.newstory.domain.ModelLine
import dev.opendungeon.feature.newstory.domain.WizardOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The fast way into a story: a premise with blanks, who the two people are if the player cares to say,
 * how far the story may go, and then the story begins, written by a model.
 *
 * Like the custom wizard, callers state what the player did and the wizard decides what it means. Verbs
 * run one at a time, show their result at once, and verbs that mean nothing where the player is are
 * ignored. Nothing is stored until the story begins: a seeded story takes seconds to set up, so it has
 * no draft to resume, and an open custom draft is never touched.
 */
public interface SeededWizard {

    /** What to show, or `null` until [start]. */
    public val state: StateFlow<SeededState?>

    /** [WizardOutcome.Left], [WizardOutcome.Begun] or [WizardOutcome.Failed], as in the custom wizard. */
    public val outcomes: Flow<WizardOutcome>

    /** Deals the first seed. Does nothing once started. */
    public fun start()

    /** The next words for one blank of the seed, written by a model once the seed's own run out. */
    public fun reroll(blank: String)

    /** Another seed, written by a model once those that come with the app run out. */
    public fun rollSeed()

    /** The player's words about one of the two people, kept as typed. */
    public fun describe(person: Person, text: String)

    /** A content limit. */
    public fun select(choice: Choice)

    /** The player's own limits, in their words. */
    public fun submitOwn(text: String)

    /** Moves on, stopping whatever a model is writing and keeping what is shown. */
    public fun next()

    /** Steps back. While the story is being written it asks first: [SeededState.confirmingLeave]. */
    public fun back()

    /** Stops what a model is writing. On the last step this goes back to the limits. */
    public fun stop()

    /** Writes the two people and the opening, then begins the story. */
    public fun begin()

    public fun answerLeave(answer: LeaveAnswer)

    /** Makes a wizard whose work lives as long as [scope], typically a screen's. */
    public fun interface Factory {
        public fun create(scope: CoroutineScope): SeededWizard
    }
}

/** The two people the player may describe before the story begins. */
public enum class Person { You, Them }

/** The steps of a seeded story, in order. */
public enum class SeededStep { Seed, People, Limits, Making }

/** Everything the seeded wizard shows, already decided. Screens only render it. */
public data class SeededState(
    val step: SeededStep,
    /** How far through the steps, from just above 0 to 1 on the last. */
    val progress: Float,
    val body: SeededBody,
    val controls: SeededControls,
    /** The model, where one writes or is about to; `null` elsewhere. */
    val writer: ModelLine?,
    /** Back was pressed while the story is being written: leaving stops it, so the player is asked first. */
    val confirmingLeave: Boolean = false,
)

/** Which controls are live. */
public data class SeededControls(
    /** Forward is live: next, or on the limits, Begin. */
    val canAdvance: Boolean,
    /** Forward begins the story. */
    val begins: Boolean,
    /** The die deals another seed. */
    val canRoll: Boolean,
    /** A model is writing something that stopping would cut short. */
    val canStop: Boolean,
    /** The step takes the player's own words in its "my own" pill. */
    val takesOwnWords: Boolean,
)

public sealed interface SeededBody {

    /** The seed's sentence, its blanks tappable. [replacing] while a model writes new seeds to replace it. */
    public data class Seed(val genre: String, val parts: List<SeedPart>, val replacing: Boolean) : SeededBody

    /** Two short fields, both optional, under the premise. */
    public data class People(
        val premise: String,
        val you: String,
        val them: String,
        /** How the premise names the other person: "your grandmother". */
        val counterpart: String,
        val maxLength: Int,
    ) : SeededBody

    public data class Limits(val premise: String, val limits: ContentLimits) : SeededBody

    /** The story being written: the two people once cast, then the opening as it streams in. */
    public data class Making(
        val premise: String,
        /** How the premise names the other person: "your grandmother". */
        val counterpart: String,
        val you: Cast?,
        val them: Cast?,
        val opening: String,
    ) : SeededBody

    /** Someone cast, as the last step names them. */
    public data class Cast(val name: String, val role: String?)
}

/** A piece of the seed's sentence. */
public sealed interface SeedPart {
    public val text: String

    public data class Text(override val text: String) : SeedPart

    /** A blank the player can tap for other words. [pending] while a model writes more words for it. */
    public data class Blank(
        val key: String,
        val kind: Seed.Kind,
        override val text: String,
        val pending: Boolean,
        /** Tapping it would do something now. */
        val canReroll: Boolean,
    ) : SeedPart
}
