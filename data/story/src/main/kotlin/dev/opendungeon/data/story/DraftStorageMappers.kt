package dev.opendungeon.data.story

import dev.opendungeon.core.database.story.DraftCharacterEntity
import dev.opendungeon.core.database.story.StoredDraft
import dev.opendungeon.core.database.story.StoryDraftEntity
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.StoryDraft

/*
 * Enum values are written as fixed storage names (see StorageNames.kt), and an unknown name falls back
 * to the safest reading instead of failing the whole draft.
 */

internal fun StoryDraft.toStored(): StoredDraft = StoredDraft(
    draft = StoryDraftEntity(
        id = id.value,
        step = step.storageName,
        genres = genres.map { it.toColumns() },
        twist = twist?.toColumns(),
        tone = tone?.toColumns(),
        setting = setting?.toColumns(),
        premise = premise?.toColumns(),
        limitDesire = limits.desire.storageName,
        limitViolence = limits.violence.storageName,
        limitNote = limits.note,
        opening = opening,
    ),
    characters =
        listOfNotNull(protagonist?.toEntity(id, Slot.PROTAGONIST, position = 0, origin = null, inStory = true)) +
            cast.mapIndexed { index, member ->
                member.character.toEntity(
                    id,
                    Slot.CAST,
                    position = index,
                    origin = member.origin,
                    inStory = member.inStory,
                )
            },
)

internal fun StoredDraft.toDomain(): StoryDraft {
    val bySlot = characters.sortedBy(DraftCharacterEntity::position).groupBy(DraftCharacterEntity::slot)
    return StoryDraft(
        id = DraftId(draft.id),
        step = CreationStep.entries.firstOrNull { it.storageName == draft.step } ?: CreationStep.Genre,
        genres = draft.genres.map { it.toPhrase() },
        twist = draft.twist?.toPhrase(),
        protagonist = bySlot[Slot.PROTAGONIST]?.firstOrNull()?.toCharacter(),
        tone = draft.tone?.toPhrase(),
        setting = draft.setting?.toPhrase(),
        limits = ContentLimits(
            desire = desireOf(draft.limitDesire),
            violence = violenceOf(draft.limitViolence),
            note = draft.limitNote,
        ),
        premise = draft.premise?.toPhrase(),
        cast = bySlot[Slot.CAST].orEmpty().map { entity ->
            CastMember(
                character = entity.toCharacter(),
                origin = CastMember.Origin.entries.firstOrNull { it.storageName == entity.origin }
                    ?: CastMember.Origin.Offered,
                inStory = entity.inStory,
            )
        },
        opening = draft.opening,
    )
}

private object Slot {
    const val PROTAGONIST = "protagonist"
    const val CAST = "cast"
}

private fun Character.toEntity(
    draftId: DraftId,
    slot: String,
    position: Int,
    origin: CastMember.Origin?,
    inStory: Boolean,
) = DraftCharacterEntity(
    id = id.value,
    draftId = draftId.value,
    slot = slot,
    position = position,
    origin = origin?.storageName,
    inStory = inStory,
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

private fun DraftCharacterEntity.toCharacter() = Character(
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

private val CreationStep.storageName: String
    get() = when (this) {
        CreationStep.Genre -> "genre"
        CreationStep.Twist -> "twist"
        CreationStep.Starring -> "starring"
        CreationStep.Tone -> "tone"
        CreationStep.Setting -> "setting"
        CreationStep.Limits -> "limits"
        CreationStep.Premise -> "premise"
        CreationStep.Cast -> "cast"
        CreationStep.Opening -> "opening"
    }

private val CastMember.Origin.storageName: String
    get() = when (this) {
        CastMember.Origin.Offered -> "offered"
        CastMember.Origin.Own -> "own"
    }
