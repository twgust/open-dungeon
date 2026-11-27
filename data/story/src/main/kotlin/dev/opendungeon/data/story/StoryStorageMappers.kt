package dev.opendungeon.data.story

import dev.opendungeon.core.database.play.BeatEntity
import dev.opendungeon.core.database.play.StoredStory
import dev.opendungeon.core.database.play.StoryCharacterEntity
import dev.opendungeon.core.database.play.StoryEntity
import dev.opendungeon.core.database.play.StoryListing
import dev.opendungeon.core.database.play.StoryProgress
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.Composer
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.StorySummary
import dev.opendungeon.domain.story.Unsent
import kotlin.time.Instant

/*
 * As for drafts, enum values are written as fixed storage names, and an unknown name falls back to the
 * safest reading instead of failing the whole story.
 */

internal fun Story.toStored(updatedAt: Long): StoredStory = StoredStory(
    story = toEntity(updatedAt),
    characters = listOfNotNull(setup.protagonist?.toEntity(id, StorySlot.PROTAGONIST, position = 0)) +
        setup.cast.mapIndexed { index, person -> person.toEntity(id, StorySlot.CAST, position = index) },
    beats = beatEntities(),
)

internal fun Story.toEntity(updatedAt: Long): StoryEntity {
    val progress = toProgress(updatedAt)
    return StoryEntity(
        id = id.value,
        title = title,
        genres = setup.genres.map { it.toColumns() },
        twist = setup.twist?.toColumns(),
        tone = setup.tone?.toColumns(),
        setting = setup.setting?.toColumns(),
        premise = setup.premise?.toColumns(),
        limitDesire = setup.limits.desire.storageName,
        limitViolence = setup.limits.violence.storageName,
        limitNote = setup.limits.note,
        guide = progress.guide,
        unsentTurn = progress.unsentTurn,
        composerKind = progress.composerKind,
        composerText = progress.composerText,
        composerBeatId = progress.composerBeatId,
        updatedAt = updatedAt,
    )
}

/** What a save writes of the story's row: see [StoryProgress]. */
internal fun Story.toProgress(updatedAt: Long): StoryProgress {
    val composer = unsent.composer
    return StoryProgress(
        id = id.value,
        guide = guide,
        unsentTurn = unsent.turn,
        composerKind = composer?.storageName,
        composerText = composer?.text,
        composerBeatId = (composer as? Composer.Edit)?.beat?.value,
        updatedAt = updatedAt,
    )
}

/** A blank stored title reads as none, so the library never shows an empty line for a name. */
internal fun StoryListing.toSummary() = StorySummary(
    id = StoryId(id),
    title = title?.takeIf(String::isNotBlank),
    genres = genres.map { it.toPhrase() },
    playedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun Story.beatEntities(): List<BeatEntity> = beats.mapIndexed { index, beat ->
    BeatEntity(id = beat.id.value, storyId = id.value, position = index, kind = beat.kind.storageName, text = beat.text)
}

/**
 * The story, or `null` when nothing of it can be played: a story always has a beat, so a row without any
 * cannot be shown and is read as missing rather than crash.
 */
internal fun StoredStory.toDomain(): Story? {
    val beats = beats.sortedBy(BeatEntity::position).map { it.toBeat() }
    if (beats.isEmpty()) return null
    val bySlot = characters.sortedBy(StoryCharacterEntity::position).groupBy(StoryCharacterEntity::slot)
    return Story(
        id = StoryId(story.id),
        title = story.title?.takeIf(String::isNotBlank),
        setup = StorySetup(
            genres = story.genres.map { it.toPhrase() },
            twist = story.twist?.toPhrase(),
            protagonist = bySlot[StorySlot.PROTAGONIST]?.firstOrNull()?.toCharacter(),
            tone = story.tone?.toPhrase(),
            setting = story.setting?.toPhrase(),
            limits = ContentLimits(
                desire = desireOf(story.limitDesire),
                violence = violenceOf(story.limitViolence),
                note = story.limitNote,
            ),
            premise = story.premise?.toPhrase(),
            cast = bySlot[StorySlot.CAST].orEmpty().map { it.toCharacter() },
        ),
        beats = beats,
        guide = story.guide,
        unsent = Unsent(turn = story.unsentTurn, composer = composerOf(beats)),
    )
}

/** An edit of a beat that is no longer there is dropped rather than shown open on nothing. */
private fun StoredStory.composerOf(beats: List<Beat>): Composer? {
    val text = story.composerText ?: return null
    return when (story.composerKind) {
        GUIDE -> Composer.Guide(text)
        REASON -> Composer.Reason(text)
        EDIT ->
            story.composerBeatId
                ?.let(::BeatId)
                ?.takeIf { id -> beats.any { it.id == id } }
                ?.let { Composer.Edit(it, text) }
        else -> null
    }
}

private object StorySlot {
    const val PROTAGONIST = "protagonist"
    const val CAST = "cast"
}

private const val GUIDE = "guide"
private const val REASON = "reason"
private const val EDIT = "edit"

private val Composer.storageName: String
    get() = when (this) {
        is Composer.Guide -> GUIDE
        is Composer.Reason -> REASON
        is Composer.Edit -> EDIT
    }

private val Beat.Kind.storageName: String
    get() = when (this) {
        Beat.Kind.Opening -> "opening"
        Beat.Kind.Turn -> "turn"
        Beat.Kind.Narration -> "narration"
    }

/** An unknown kind is read as the player's own words, so a model can never be asked to write it again. */
private fun BeatEntity.toBeat() = Beat(
    id = BeatId(id),
    kind = Beat.Kind.entries.firstOrNull { it.storageName == kind } ?: Beat.Kind.Turn,
    text = text,
)

private fun Character.toEntity(storyId: StoryId, slot: String, position: Int) = StoryCharacterEntity(
    id = id.value,
    storyId = storyId.value,
    slot = slot,
    position = position,
    role = role,
    name = name,
    age = age,
    gender = gender,
    appearance = appearance,
    personality = personality,
    speech = speech,
    voiceLine = voiceLine,
    wants = wants,
    flaw = flaw,
    habit = habit,
    goodAt = goodAt,
    secretKind = secret.storageName,
    secretText = secret.storedText,
)

private fun StoryCharacterEntity.toCharacter() = Character(
    id = CharacterId(id),
    role = role,
    name = name,
    age = age,
    gender = gender,
    appearance = appearance,
    personality = personality,
    speech = speech,
    voiceLine = voiceLine,
    wants = wants,
    flaw = flaw,
    habit = habit,
    goodAt = goodAt,
    secret = secretOf(secretKind, secretText),
)
