package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.WriteOpening
import dev.opendungeon.domain.drafting.WritingProgress
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.DraftEdit
import dev.opendungeon.feature.newstory.domain.StepBody
import dev.opendungeon.feature.newstory.domain.StepPolicy
import dev.opendungeon.feature.newstory.domain.StepWriter
import dev.opendungeon.feature.newstory.domain.WriterEvent
import dev.opendungeon.feature.newstory.domain.WriterStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * "The opening": how the story begins, in one text the player owns. It starts empty. The wand writes
 * the whole opening when the text is empty and carries on after it when it is not; either way a model
 * only ever adds after the player's text, never changes it. Stopping keeps what was written so far,
 * undo takes back exactly what the model added, and a failure leaves the text as it was and says so on
 * the step. The story can begin once the text has something in it.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<StepPolicy>())
public class OpeningPolicy(private val writeOpening: WriteOpening) :
    StepPolicy,
    StepWriter {

    override val step: CreationStep = CreationStep.Opening

    override fun body(draft: StoryDraft): StepBody = body(draft, WriterStatus.Idle)

    /** While a model writes, the text is the one it started from, and its words stay apart until they are kept. */
    override fun body(draft: StoryDraft, writer: WriterStatus): StepBody {
        val writing = writer as? WriterStatus.Writing
        val text = (writing?.from ?: draft).opening
        return StepBody.Opening(
            text = text,
            writing = writing?.let { run ->
                run.preview?.let { StepBody.Opening.Writing.Adding(it.opening.drop(text.length)) }
                    ?: StepBody.Opening.Writing.Waiting
            },
            wand = if (text.isBlank()) StepBody.Opening.Wand.WriteForMe else StepBody.Opening.Wand.ContinueMyText,
            addition = (writer as? WriterStatus.Kept)?.let { StepBody.Opening.Addition(stopped = it.stopped) },
            failed = writer is WriterStatus.Failed,
            maxLength = StoryDraft.MAX_OPENING_LENGTH,
        )
    }

    override fun canAdvance(draft: StoryDraft): Boolean = draft.opening.isNotBlank()

    /** The wand, while there is room left to add to. */
    override fun canRoll(draft: StoryDraft): Boolean = draft.opening.length < StoryDraft.MAX_OPENING_LENGTH

    /** Kept as typed, line breaks and all, up to the limit. */
    override fun edit(draft: StoryDraft, edit: DraftEdit): StoryDraft? =
        (edit as? DraftEdit.Opening)?.let { draft.copy(opening = it.text.take(StoryDraft.MAX_OPENING_LENGTH)) }

    override val writer: StepWriter get() = this

    /** The field starts empty with its hint; writing waits for the wand. */
    override fun writesOnArrival(draft: StoryDraft): Boolean = false

    override val keepsWhenStopped: Boolean get() = true

    override val undoable: Boolean get() = true

    override val failsInPlace: Boolean get() = true

    override fun write(draft: StoryDraft, model: DraftingModel): Flow<WriterEvent> =
        writeOpening(draft, model).map { progress ->
            when (progress) {
                is WritingProgress.Writing -> WriterEvent.Preview(draft.adding(progress.text))
                is WritingProgress.Written -> WriterEvent.Done(draft.adding(progress.text))
                is WritingProgress.Failed -> WriterEvent.Failed(progress.error)
            }
        }

    /**
     * The player's text with [addition] after it, cut at the limit. Only the addition can be cut, since
     * the text before it is never longer than the limit.
     */
    private fun StoryDraft.adding(addition: String): StoryDraft =
        copy(opening = (opening + addition).take(StoryDraft.MAX_OPENING_LENGTH))
}
