package dev.opendungeon.domain.story

import kotlin.time.Instant

/** What the library shows of a story without reading it whole. */
public data class StorySummary(
    val id: StoryId,
    /** `null` until the story is named. */
    val title: String?,
    val genres: List<Phrase>,
    /** When the story was last played: saving it is playing it. Naming it is not. */
    val playedAt: Instant,
)
