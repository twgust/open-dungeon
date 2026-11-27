package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.CastPeople
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.PersonCard
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.opendungeon.feature.newstory.domain.StepWriter
import dev.opendungeon.feature.newstory.domain.WriterEvent
import dev.opendungeon.feature.newstory.domain.filledLines
import dev.opendungeon.feature.newstory.domain.shown
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * "Characters": the people on offer, each in or out of the story. At least one has to be in. A model
 * casts people on arrival; the die swaps everyone not yet in the story for new people. A card shows a
 * sealed secret only as a flag.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<StepPolicy>())
public class CastPolicy(private val castPeople: CastPeople) :
    StepPolicy,
    StepWriter {

    override val step: CreationStep = CreationStep.Cast

    override fun body(draft: StoryDraft): StepBody = StepBody.Cast(draft.cast.map { it.toCard() })

    override fun canAdvance(draft: StoryDraft): Boolean = draft.cast.any { it.inStory }

    override fun canRoll(draft: StoryDraft): Boolean = true

    override fun select(draft: StoryDraft, choice: Choice): Selection? {
        if (choice !is Choice.CastMember || draft.cast.none { it.character.id == choice.id }) return null
        val cast = draft.cast.map { if (it.character.id == choice.id) it.copy(inStory = !it.inStory) else it }
        return Selection(draft.copy(cast = cast), advance = false)
    }

    override val writer: StepWriter get() = this

    override fun writesOnArrival(draft: StoryDraft): Boolean = draft.cast.isEmpty()

    /**
     * New people join on offer, after everyone the player keeps: those taken into the story and those
     * the player wrote. The new ones wait to be taken, so who is in the story is always the player's say.
     */
    override fun write(draft: StoryDraft, model: DraftingModel): Flow<WriterEvent> = flow {
        val event = when (val cast = castPeople(draft, model)) {
            is Outcome.Failure -> WriterEvent.Failed(cast.error)
            is Outcome.Success -> {
                val kept = draft.cast.filter { it.inStory || it.origin == CastMember.Origin.Own }
                val offered = cast.value.map { CastMember(it, CastMember.Origin.Offered, inStory = false) }
                WriterEvent.Done(draft.copy(cast = kept + offered))
            }
        }
        emit(event)
    }

    private fun CastMember.toCard() = PersonCard(
        id = character.id,
        role = character.role,
        name = character.name,
        appearance = character.appearance,
        lines = character.filledLines(CARD_LINES),
        secret = character.secret.shown(),
        inStory = inStory,
    )

    private companion object {
        /** What a cast card shows under the name, in this order: what drives them, a tell, how they sound. */
        val CARD_LINES = listOf(CharacterLine.Wants, CharacterLine.Habit, CharacterLine.VoiceLine)
    }
}
