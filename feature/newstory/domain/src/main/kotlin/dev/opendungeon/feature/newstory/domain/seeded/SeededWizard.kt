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
 * The fast way into a story: a premise with blanks the player fills, who the two people are if the player
 * cares to say, how far the story may go, and then the story begins, written by a model.
 *
 * Like the custom wizard, callers state what the player did and the wizard decides what it means. Verbs
 * run one at a time, show their result at once, and verbs that mean nothing where the player is are
 * ignored. Nothing is stored until the story begins: a seeded story takes seconds to set up, so it has
 * no draft to resume, and an open custom draft is never touched.
 *
 * There is one function for each thing the player can do, so the functions are many by design.
 */
@Suppress("TooManyFunctions")
public interface SeededWizard {

    /** What to show, or `null` until [start]. */
    public val state: StateFlow<SeededState?>

    /** [WizardOutcome.Left], [WizardOutcome.Begun] or [WizardOutcome.Failed], as in the custom wizard. */
    public val outcomes: Flow<WizardOutcome>

    /** Deals the first seed. Does nothing once started. */
    public fun start()

    /** Picks one blank of the seed, to show the words that could fill it. */
    public fun pick(blank: String)

    /**
     * Puts [words] in [blank], either one of its suggestions or the player's own, and picks the next blank
     * still empty. Braces and line breaks are left out and long words cut, so the player's own always fit.
     */
    public fun fill(blank: String, words: String)

    /** Asks a model for more words for [blank]. The only way a blank's words come from a model. */
    public fun suggestMore(blank: String)

    /** Another seed, its blanks empty, written by a model once those that come with the app run out. */
    public fun rollSeed()

    /** The player's words about one of the two people, kept as typed. */
    public fun describe(person: Person, text: String)

    /** A content limit. */
    public fun select(choice: Choice)

    /** The player's own limits, in their words. */
    public fun submitOwn(text: String)

    /** Moves on once every blank is filled, stopping whatever a model is writing and keeping what is shown. */
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
    /** Forward is live: next once every blank is filled, or on the limits, Begin. */
    val canAdvance: Boolean,
    /** Forward begins the story. */
    val begins: Boolean,
    /** The die deals another seed. */
    val canRoll: Boolean,
    /** A model is writing something that stopping would cut short. */
    val canStop: Boolean,
    /** The step takes the player's own words in its "Custom" pill: the limits, or the picked blank. */
    val takesOwnWords: Boolean,
)

public sealed interface SeededBody {

    /**
     * The seed's sentence, its blanks tappable, empty until the player fills them, and under it the words
     * for the picked blank. [replacing] while a model writes new seeds to replace it.
     */
    public data class Seed(
        val genre: String,
        val parts: List<SeedPart>,
        /** The words for the picked blank; `null` when none is picked. */
        val suggestions: Suggestions?,
        val replacing: Boolean,
    ) : SeededBody

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

    public data class Text(val text: String) : SeedPart

    /** A blank the player taps to pick it. */
    public data class Blank(
        val key: String,
        val kind: Seed.Kind,
        /** The words the player put in it, capitalised where they open the sentence; `null` while empty. */
        val words: String?,
        /** Its suggestions are the ones shown. */
        val picked: Boolean,
    ) : SeedPart
}

/** The words that could fill one blank, in the order they were written. */
public data class Suggestions(
    val blank: String,
    val kind: Seed.Kind,
    val words: List<String>,
    /** The words in the blank now, if any. */
    val chosen: String?,
    /** A model is writing more. */
    val pending: Boolean,
    /** Asking for more would do something now. */
    val canSuggestMore: Boolean,
)
