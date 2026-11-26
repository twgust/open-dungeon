package dev.opendungeon.domain.story

/** A story being created. Persisted after every change, so creation survives the app being killed. */
public data class StoryDraft(
    val id: DraftId,
    val step: CreationStep = CreationStep.Genre,
    val genres: List<Phrase> = emptyList(),
    val twist: Phrase? = null,
    val protagonist: Character? = null,
    val tone: Phrase? = null,
    val setting: Phrase? = null,
    val limits: ContentLimits = ContentLimits(),
    val premise: Phrase? = null,
    val cast: List<CastMember> = emptyList(),
    /** How the story begins, as the player wrote it or kept it from a model. Play starts from it. */
    val opening: String = "",
) {
    val castInStory: List<Character>
        get() = cast.filter(CastMember::inStory).map(CastMember::character)

    public companion object {
        public const val MAX_GENRES: Int = 2
        public const val MAX_OPENING_LENGTH: Int = 4_000
    }
}
