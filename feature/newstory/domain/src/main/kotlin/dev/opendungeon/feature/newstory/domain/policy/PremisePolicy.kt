package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.GeneratePremise
import dev.opendungeon.domain.drafting.WritingProgress
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.Selection
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.opendungeon.feature.newstory.domain.StepWriter
import dev.opendungeon.feature.newstory.domain.WriterEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The premise, editable in place. A model writes it on arrival and again with the die; the
 * player can edit it or write their own instead. The step needs text to be left.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<StepPolicy>())
public class PremisePolicy(private val generatePremise: GeneratePremise) :
    StepPolicy,
    StepWriter {

    override val step: CreationStep = CreationStep.Premise

    override fun body(draft: StoryDraft): StepBody =
        StepBody.Premise(text = draft.premise?.text.orEmpty(), own = draft.premise is Phrase.Own)

    override fun canAdvance(draft: StoryDraft): Boolean = draft.premise != null

    override fun canRoll(draft: StoryDraft): Boolean = true

    override fun takesOwnWords(draft: StoryDraft): Boolean = true

    override fun submitOwn(draft: StoryDraft, text: String): Selection =
        Selection(draft.withPremise(text.trim()), advance = false)

    /** Kept as typed, so the field being edited reads back exactly what it holds. */
    override fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? =
        (edit as? DraftEdit.Premise)?.let { draft.withPremise(it.text) }

    override val writer: StepWriter get() = this

    override fun writesOnArrival(draft: StoryDraft): Boolean = draft.premise == null

    /** A written premise is an offered one, until the player changes a word of it. */
    override fun write(draft: StoryDraft, model: DraftingModel): Flow<WriterEvent> =
        generatePremise(draft, model).map { progress ->
            when (progress) {
                is WritingProgress.Writing -> WriterEvent.Preview(draft.copy(premise = Phrase.Offered(progress.text)))
                is WritingProgress.Written -> WriterEvent.Done(draft.copy(premise = Phrase.Offered(progress.text)))
                is WritingProgress.Failed -> WriterEvent.Failed(progress.error)
            }
        }

    /** Any edit makes the premise the player's own words, even one that started as a generated one. Blank clears it. */
    private fun StoryDraft.withPremise(text: String) = copy(premise = text.takeIf(String::isNotBlank)?.let(Phrase::Own))
}
