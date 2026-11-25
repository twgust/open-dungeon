package dev.opendungeon.data.story

import dev.opendungeon.core.database.story.DraftCharacterEntity
import dev.opendungeon.core.database.story.DraftTakeEntity
import dev.opendungeon.core.database.story.PhraseColumns
import dev.opendungeon.core.database.story.StoredDraft
import dev.opendungeon.core.database.story.StoryDraftEntity
import dev.opendungeon.core.model.ModelId
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.Take
import dev.opendungeon.domain.story.TakeId

/*
 * Enum values are written as fixed storage names rather than `name`, so renaming a Kotlin constant
 * never changes what is on disk. An unknown name falls back to the safest reading instead of failing
 * the whole draft.
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
        firstLine = firstLine,
        chosenTakeId = chosenTake?.value,
    ),
    characters = listOfNotNull(protagonist?.toEntity(id, Slot.Protagonist, position = 0, origin = null, inStory = true)) +
        cast.mapIndexed { index, member ->
            member.character.toEntity(id, Slot.Cast, position = index, origin = member.origin, inStory = member.inStory)
        },
    takes = takes.mapIndexed { index, take -> take.toEntity(id, index) },
)

internal fun StoredDraft.toDomain(): StoryDraft {
    val bySlot = characters.sortedBy(DraftCharacterEntity::position).groupBy(DraftCharacterEntity::slot)
    return StoryDraft(
        id = DraftId(draft.id),
        step = CreationStep.entries.firstOrNull { it.storageName == draft.step } ?: CreationStep.Genre,
        genres = draft.genres.map { it.toPhrase() },
        twist = draft.twist?.toPhrase(),
        protagonist = bySlot[Slot.Protagonist]?.firstOrNull()?.toCharacter(),
        tone = draft.tone?.toPhrase(),
        setting = draft.setting?.toPhrase(),
        limits = ContentLimits(
            desire = ContentLimits.Desire.entries.firstOrNull { it.storageName == draft.limitDesire }
                ?: ContentLimits.Desire.Unspoken,
            violence = ContentLimits.Violence.entries.firstOrNull { it.storageName == draft.limitViolence }
                ?: ContentLimits.Violence.Offstage,
            note = draft.limitNote,
        ),
        premise = draft.premise?.toPhrase(),
        cast = bySlot[Slot.Cast].orEmpty().map { entity ->
            CastMember(
                character = entity.toCharacter(),
                origin = CastMember.Origin.entries.firstOrNull { it.storageName == entity.origin }
                    ?: CastMember.Origin.Offered,
                inStory = entity.inStory,
            )
        },
        firstLine = draft.firstLine,
        takes = takes.sortedBy(DraftTakeEntity::position).map { it.toTake() },
        chosenTake = draft.chosenTakeId?.let(::TakeId),
    )
}

private object Slot {
    const val Protagonist = "protagonist"
    const val Cast = "cast"
}

private fun Phrase.toColumns() = PhraseColumns(text = text, own = this is Phrase.Own)

/** Blank own words cannot be built, so a blank stored phrase is read back as an offered one. */
private fun PhraseColumns.toPhrase(): Phrase =
    if (own && text.isNotBlank()) Phrase.Own(text) else Phrase.Offered(text)

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
    secretText = when (val hidden = secret) {
        Secret.None -> null
        is Secret.Known -> hidden.text
        is Secret.Sealed -> hidden.text
    },
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
    secret = secretText?.let { text ->
        // Anything but a known secret stays hidden: a secret must never leak through a misread.
        if (secretKind == KNOWN_SECRET) Secret.Known(text) else Secret.Sealed(text)
    } ?: Secret.None,
)

private fun Take.toEntity(draftId: DraftId, position: Int) = DraftTakeEntity(
    id = id.value,
    draftId = draftId.value,
    position = position,
    modelId = model.value,
    text = text,
    state = state.storageName,
)

/** A take that was still writing when it was stored did not finish: the writer is gone. */
private fun DraftTakeEntity.toTake() = Take(
    id = TakeId(id),
    model = ModelId(modelId),
    text = text,
    state = when (state) {
        WRITTEN_TAKE -> Take.State.Written
        else -> Take.State.Interrupted
    },
)

private const val KNOWN_SECRET = "known"
private const val WRITTEN_TAKE = "written"

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
        CreationStep.FirstLine -> "first_line"
        CreationStep.FirstTurn -> "first_turn"
    }

private val ContentLimits.Desire.storageName: String
    get() = when (this) {
        ContentLimits.Desire.Unspoken -> "unspoken"
        ContentLimits.Desire.Close -> "close"
        ContentLimits.Desire.AllTheWay -> "all_the_way"
    }

private val ContentLimits.Violence.storageName: String
    get() = when (this) {
        ContentLimits.Violence.Offstage -> "offstage"
        ContentLimits.Violence.Felt -> "felt"
        ContentLimits.Violence.Shown -> "shown"
    }

private val CastMember.Origin.storageName: String
    get() = when (this) {
        CastMember.Origin.Offered -> "offered"
        CastMember.Origin.Own -> "own"
    }

private val Secret.storageName: String
    get() = when (this) {
        Secret.None -> "none"
        is Secret.Known -> KNOWN_SECRET
        is Secret.Sealed -> "sealed"
    }

private val Take.State.storageName: String
    get() = when (this) {
        Take.State.Writing -> "writing"
        Take.State.Written -> WRITTEN_TAKE
        Take.State.Interrupted -> "interrupted"
    }
