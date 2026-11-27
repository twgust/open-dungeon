package dev.opendungeon.domain.story

/**
 * A story being played: what the player chose for it, the beats so far, and what the player is writing
 * and has not used yet. Persisted after every change, so play survives the app being killed.
 */
public data class Story(
    val id: StoryId,
    val setup: StorySetup,
    /** Oldest first. A story always has at least one beat: it begins with its opening. */
    val beats: List<Beat>,
    /** The player's direction for the next beat a model writes. Used once, then cleared. */
    val guide: String? = null,
    val unsent: Unsent = Unsent(),
) {
    init {
        require(beats.isNotEmpty()) { "A story begins with its opening, so it always has a beat" }
    }

    public companion object {
        /** Longest beat, typed or written: a long scene with room to spare, not a chapter. */
        public const val MAX_BEAT_LENGTH: Int = 8_000

        /** Longest turn the player can send. */
        public const val MAX_TURN_LENGTH: Int = 4_000

        /** Longest direction, or reason for a retry: pointers, not a script. */
        public const val MAX_NOTE_LENGTH: Int = 1_000
    }
}

/** What the player chose for a story, fixed when it begins. Only the people in the story are kept. */
public data class StorySetup(
    override val genres: List<Phrase> = emptyList(),
    override val twist: Phrase? = null,
    override val protagonist: Character? = null,
    override val tone: Phrase? = null,
    override val setting: Phrase? = null,
    override val limits: ContentLimits = ContentLimits(),
    override val premise: Phrase? = null,
    val cast: List<Character> = emptyList(),
) : StoryChoices {
    override val castInStory: List<Character> get() = cast

    public companion object {
        /** The choices [draft] holds, with the people left out of the story dropped. */
        public fun of(draft: StoryDraft): StorySetup = StorySetup(
            genres = draft.genres,
            twist = draft.twist,
            protagonist = draft.protagonist,
            tone = draft.tone,
            setting = draft.setting,
            limits = draft.limits,
            premise = draft.premise,
            cast = draft.castInStory,
        )
    }
}

/** One passage of the story, set apart from the next on the page. */
public data class Beat(val id: BeatId, val kind: Kind, val text: String) {
    public enum class Kind {
        /** How the story begins, from story creation. */
        Opening,

        /** What the player wrote for their character to do or say. */
        Turn,

        /** What a model wrote, as it wrote it or as the player edited it since. */
        Narration,
    }
}

/**
 * What the player is writing on a story and has not used yet. Kept with the story, so leaving the
 * screen or the app dying loses none of it.
 */
public data class Unsent(
    /** The player's next turn, as typed. */
    val turn: String = "",
    /** The note or edit the player has open, if any. */
    val composer: Composer? = null,
)

/** A note or an edit the player has open under the story, with its text as typed so far. */
public sealed interface Composer {
    public val text: String

    /** A direction for the next beat. */
    public data class Guide(override val text: String) : Composer

    /** What was wrong with the last beat, for writing it again. */
    public data class Reason(override val text: String) : Composer

    /** [beat] being edited in place; [text] is its new text. */
    public data class Edit(val beat: BeatId, override val text: String) : Composer
}
