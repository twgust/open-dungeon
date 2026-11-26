package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.ResolveDraftingModel
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.Sentence
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A thin coordinator: it opens the draft, hands each verb to the current step's [StepPolicy], and keeps
 * what comes back.
 *
 * Verbs run one at a time on a single loop, which is the draft's only writer while the wizard lives.
 * Each verb shows its result at once and then saves it; a save that fails takes the verb back and
 * reports why. Typing is the exception: text is shown as typed and saved once it settles, so a burst of
 * keystrokes is one write, and text that could not be saved stays on screen for the next save. Whatever
 * is still unsaved when the wizard's scope ends is saved on the way out.
 *
 * A step a model writes is the other exception, since writing takes seconds and the player must be able
 * to stop it. The writing runs beside the loop and hands each piece back to it; while it runs, every
 * verb but stopping and leaving waits. What it shows on the way is never saved, and only a result is
 * kept, so failing or leaving leaves the draft as it was. Stopping does too, unless the step's writer
 * keeps what it wrote so far. What a writer kept can be taken back where the writer allows it, until
 * the draft changes again.
 */
internal class DefaultStoryWizard(
    private val scope: CoroutineScope,
    private val repository: StoryDraftRepository,
    policies: Set<StepPolicy>,
    private val sentences: SentenceBuilder,
    private val ids: IdGenerator,
    drafter: ResolveDraftingModel,
    private val typingSettles: Duration = TYPING_SETTLES,
) : StoryWizard {

    private val policies: Map<CreationStep, StepPolicy> = policies.associateBy(StepPolicy::step).also { byStep ->
        require(byStep.size == policies.size) { "More than one policy for a step" }
        require(byStep.keys == CreationStep.entries.toSet()) {
            "No policy for ${CreationStep.entries - byStep.keys}"
        }
    }

    private val mutableState = MutableStateFlow<WizardState?>(null)
    override val state: StateFlow<WizardState?> = mutableState.asStateFlow()

    private val outcomeChannel = Channel<WizardOutcome>(Channel.BUFFERED)
    override val outcomes: Flow<WizardOutcome> = outcomeChannel.receiveAsFlow()

    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    // Read and written by the action loop only, so they need no locking.

    /** The draft on screen. */
    private var draft: StoryDraft? = null

    /** The draft as last written to the repository. */
    private var stored: StoryDraft? = null

    /** Saves typed text once typing pauses. */
    private var settling: Job? = null

    /** The model that writes, once known; `null` when none is chosen. */
    private var drafting: DraftingModel? = null
    private var draftingKnown = false

    /** The model writing the current step, if one is. */
    private var writing: Writing? = null

    /** What the last writing left for the step to offer: its result to take back, or its failure. */
    private var aftermath: Aftermath? = null

    private var confirmingLeave = false

    init {
        scope.launch {
            try {
                for (action in actions) runIsolated(action)
            } finally {
                withContext(NonCancellable) { saveOnTheWayOut() }
            }
        }
        scope.launch {
            drafter()
                .catch { cause ->
                    // A failing source is a bug below; the wizard goes on without a model rather than die.
                    if (cause is CancellationException || cause !is Exception) throw cause
                    enqueue {
                        onDrafter(null)
                        report(WizardFailure.Unexpected(cause))
                    }
                }
                .collect { model -> enqueue { onDrafter(model) } }
        }
    }

    override fun start(draft: DraftId?) = enqueue {
        if (this.draft != null) return@enqueue
        val resumed = when (val read = resume(draft)) {
            is Outcome.Failure -> return@enqueue report(WizardFailure.Storage(read.error))
            is Outcome.Success -> read.value
        }
        if (resumed != null) {
            stored = resumed
            show(resumed)
            writeOnArrival()
            return@enqueue
        }
        // A new draft is shown only once it exists, so a failure leaves nothing half open.
        val fresh = StoryDraft(DraftId(ids.next()))
        when (val saved = save(fresh)) {
            is Outcome.Failure -> report(WizardFailure.Storage(saved.error))
            is Outcome.Success -> show(fresh)
        }
    }

    override fun select(choice: Choice) = change { policy, draft -> policy.select(draft, choice)?.let(::settle) }

    override fun submitOwn(text: String) = change { policy, draft -> policy.submitOwn(draft, text)?.let(::settle) }

    override fun edit(edit: DraftEdit) = enqueue {
        val before = draft?.takeIf { writing == null } ?: return@enqueue
        val after = policyOf(before).edit(before, edit)?.takeIf { it != before } ?: return@enqueue
        aftermath = null
        show(after)
        settling?.cancel()
        settling = scope.launch {
            delay(typingSettles)
            enqueue(::saveTyping)
        }
    }

    override fun roll(line: CharacterLine?) {
        if (line != null) rollLine(line) else rollStep()
    }

    private fun rollStep() = enqueue {
        val before = draft?.takeIf { writing == null } ?: return@enqueue
        val policy = policyOf(before)
        if (!policy.canRoll(before)) return@enqueue
        val writer = policy.writer
        if (writer != null) {
            startWriting(writer, before)
            return@enqueue
        }
        val after = policy.roll(before)?.let(::settle)?.takeIf { it != before } ?: return@enqueue
        commit(before, after)
    }

    private fun rollLine(line: CharacterLine) = change { policy, draft -> policy.rollLine(draft, line) }

    override fun next() = change { policy, draft -> if (policy.canAdvance(draft)) draft.forward() else null }

    override fun back() = enqueue {
        if (writing != null) {
            confirmingLeave = true
            render()
        } else {
            goBack()
        }
    }

    override fun stop() = enqueue {
        val run = writing ?: return@enqueue
        stopWriting()
        val kept = run.preview?.takeIf { run.writer.keepsWhenStopped && it != run.from }
        if (kept == null) {
            render()
        } else {
            commit(before = run.from, after = kept, aftermath = keptBy(run, kept, stopped = true))
        }
    }

    override fun undo() = enqueue {
        val kept = aftermath as? Aftermath.Kept ?: return@enqueue
        val current = draft?.takeIf { writing == null && it == kept.after } ?: return@enqueue
        commit(before = current, after = kept.before)
    }

    override fun begin() = enqueue {
        val current = draft?.takeIf { writing == null } ?: return@enqueue
        if (current.step.following != null || !policyOf(current).canAdvance(current)) return@enqueue
        when (val saved = save(current)) {
            is Outcome.Failure -> report(WizardFailure.Storage(saved.error))
            is Outcome.Success -> outcomeChannel.send(WizardOutcome.Begun(current.id))
        }
    }

    override fun answerLeave(answer: LeaveAnswer) = enqueue {
        if (!confirmingLeave) return@enqueue
        when (answer) {
            LeaveAnswer.Stay -> {
                confirmingLeave = false
                render()
            }
            LeaveAnswer.Leave -> {
                stopWriting()
                render()
                goBack()
            }
        }
    }

    private suspend fun goBack() {
        val current = draft
        val previous = current?.step?.previous
        if (current == null || previous == null) {
            // Leaving keeps the draft to resume, so whatever was typed is saved first.
            if (current != null) saveTyping()
            outcomeChannel.send(WizardOutcome.Left)
            return
        }
        commit(before = current, after = current.copy(step = previous))
    }

    private fun enqueue(action: suspend () -> Unit) {
        actions.trySend(action)
    }

    /** Runs [transform] against the draft on screen and keeps the result when it changed anything. */
    private fun change(transform: suspend (StepPolicy, StoryDraft) -> StoryDraft?) = enqueue {
        val before = draft?.takeIf { writing == null } ?: return@enqueue
        val after = transform(policyOf(before), before)?.takeIf { it != before } ?: return@enqueue
        commit(before, after)
    }

    /**
     * Shows [after] at once, then saves it; if the save fails, [before] comes back, with what the last
     * writing left, and the failure is reported. Any change replaces what the last writing left with
     * [aftermath], which is nothing unless the change is that writing's own result.
     */
    private suspend fun commit(before: StoryDraft, after: StoryDraft, aftermath: Aftermath? = null) {
        val aftermathBefore = this.aftermath
        this.aftermath = aftermath
        show(after)
        val saved = save(after)
        if (saved is Outcome.Failure) {
            this.aftermath = aftermathBefore
            show(before)
            report(WizardFailure.Storage(saved.error))
        } else if (after.step != before.step) {
            writeOnArrival()
        }
    }

    private suspend fun onDrafter(model: DraftingModel?) {
        drafting = model
        draftingKnown = true
        render()
        writeOnArrival()
    }

    /** A step a model writes starts writing on arrival when it has nothing to show and a model can write it. */
    private suspend fun writeOnArrival() {
        val current = draft ?: return
        if (writing != null || drafting == null) return
        val writer = policyOf(current).writer ?: return
        if (writer.writesOnArrival(current)) startWriting(writer, current)
    }

    /**
     * Writes the current step beside the loop. Each piece comes back through the loop, tagged with this
     * run, so a piece that arrives after the run was stopped, or replaced, is dropped.
     */
    private suspend fun startWriting(writer: StepWriter, from: StoryDraft) {
        val model = drafting ?: return report(WizardFailure.Drafting(DraftingError.NoModel))
        val run = Writing(writer, from)
        run.job = scope.launch(start = CoroutineStart.LAZY) { write(run, model) }
        writing = run
        aftermath = null
        render()
        run.job.start()
    }

    /** Runs off the loop: it only hands pieces to it. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun write(run: Writing, model: DraftingModel) {
        try {
            run.writer.write(run.from, model).collect { event -> enqueue { onWritten(run, event) } }
            // Reached only when the writer ended without its last word, which no writer should do.
            enqueue { endUnexpectedly(run, IllegalStateException("${run.from.step} writer ended without a result")) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // As in [runIsolated]: a writer that throws has a bug, and the wizard outlives it.
            enqueue { endUnexpectedly(run, e) }
        }
    }

    private suspend fun onWritten(run: Writing, event: WriterEvent) {
        if (writing !== run) return
        when (event) {
            is WriterEvent.Preview -> {
                run.preview = event.draft
                render()
            }
            is WriterEvent.Done -> {
                endWriting()
                val before = draft ?: return
                commit(before, after = event.draft, aftermath = keptBy(run, event.draft, stopped = false))
            }
            is WriterEvent.Failed -> {
                endWriting()
                if (run.writer.failsInPlace) {
                    aftermath = Aftermath.Failed(event.error)
                    render()
                } else {
                    render()
                    report(WizardFailure.Drafting(event.error))
                }
            }
        }
    }

    /** What keeping [kept] leaves to take back, if the run's writer lets it be taken back. */
    private fun keptBy(run: Writing, kept: StoryDraft, stopped: Boolean): Aftermath? =
        if (run.writer.undoable) Aftermath.Kept(before = run.from, after = kept, stopped = stopped) else null

    private suspend fun endUnexpectedly(run: Writing, cause: Exception) {
        if (writing !== run) return
        endWriting()
        render()
        report(WizardFailure.Unexpected(cause))
    }

    private fun stopWriting() {
        writing?.job?.cancel()
        endWriting()
    }

    /** Writing is over, so leaving no longer stops anything and needs no asking. */
    private fun endWriting() {
        writing = null
        confirmingLeave = false
    }

    /** Saves typed text that is not stored yet. On failure the text stays on screen and is saved with the next change. */
    private suspend fun saveTyping() {
        val current = draft ?: return
        val saved = save(current)
        if (saved is Outcome.Failure) report(WizardFailure.Storage(saved.error))
    }

    private suspend fun save(draft: StoryDraft): Outcome<Unit, StorageError> {
        settling?.cancel()
        if (draft == stored) return Outcome.Success(Unit)
        return repository.save(draft).also { if (it is Outcome.Success) stored = draft }
    }

    /** Nobody is listening once the scope has ended, so a failure here can only be dropped. */
    private suspend fun saveOnTheWayOut() {
        val current = draft ?: return
        if (current != stored) repository.save(current)
    }

    /** [id] if it exists, or else the open draft. A failed read is never mistaken for "no draft". */
    private suspend fun resume(id: DraftId?): Outcome<StoryDraft?, StorageError> {
        if (id != null) {
            when (val asked = repository.draft(id)) {
                is Outcome.Failure -> return asked
                is Outcome.Success -> if (asked.value != null) return asked
            }
        }
        return repository.openDraft()
    }

    /**
     * The loop is the wizard's boundary, so one verb that throws must not end every verb after it. A
     * throw here is a bug in a rule, never an expected failure (those come back typed), so it is reported
     * as [WizardFailure.Unexpected] with its cause, and the verb is taken back.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runIsolated(action: suspend () -> Unit) {
        val draftBefore = draft
        val stateBefore = mutableState.value
        val writingBefore = writing
        val aftermathBefore = aftermath
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Writing the verb started is stopped with it, so the step is never left waiting on nothing.
            if (writing != null && writing !== writingBefore) stopWriting()
            draft = draftBefore
            aftermath = aftermathBefore
            mutableState.value = stateBefore
            report(WizardFailure.Unexpected(e))
        }
    }

    private suspend fun report(failure: WizardFailure) {
        outcomeChannel.send(WizardOutcome.Failed(failure))
    }

    /** Builds the state before taking the draft, so a policy that fails to describe it changes nothing. */
    private fun show(draft: StoryDraft) {
        mutableState.value = stateOf(draft)
        this.draft = draft
    }

    /** Shows the draft on screen again, for a change around it: writing, the model, a question. */
    private fun render() {
        draft?.let(::show)
    }

    /** A selection that completes its step moves on, unless the step still cannot be left. */
    private fun settle(selection: Selection): StoryDraft {
        val draft = selection.draft
        return if (selection.advance && policyOf(draft).canAdvance(draft)) draft.forward() else draft
    }

    private fun StoryDraft.forward(): StoryDraft = step.following?.let { copy(step = it) } ?: this

    private fun policyOf(draft: StoryDraft): StepPolicy = policies.getValue(draft.step)

    /** While a model writes, the step shows what it has so far, and its controls wait. */
    private fun stateOf(draft: StoryDraft): WizardState {
        val policy = policyOf(draft)
        val run = writing
        val idle = run == null
        return WizardState(
            draft = draft.id,
            step = draft.step,
            progress = (draft.step.ordinal + 1f) / CreationStep.entries.size,
            lead = leadOf(draft),
            body = policy.body(draft, writerStatus(run)),
            controls = StepControls(
                canGoBack = true,
                canAdvance = idle && policy.canAdvance(draft),
                begins = draft.step.following == null,
                advancesOnPick = policy.advancesOnPick,
                canRoll = idle && policy.canRoll(draft),
                takesOwnWords = idle && policy.takesOwnWords(draft),
            ),
            writer = policy.writer?.takeIf { draftingKnown }?.let {
                ModelLine(model = drafting?.model?.name, writing = !idle)
            },
            confirmingLeave = confirmingLeave,
        )
    }

    private fun writerStatus(run: Writing?): WriterStatus = when {
        run != null -> WriterStatus.Writing(from = run.from, preview = run.preview)
        else -> when (val left = aftermath) {
            null -> WriterStatus.Idle
            is Aftermath.Kept -> WriterStatus.Kept(left.stopped)
            is Aftermath.Failed -> WriterStatus.Failed(left.error)
        }
    }

    /**
     * The words above the step's prompt. On the sentence steps it is the story so far, left open for the
     * prompt to finish ("A gothic noir story," above "twisted by"); on the limits it is the whole story
     * clause, and on the opening it is the whole story, cast and all. The other steps lead with their
     * prompt alone.
     */
    private fun leadOf(draft: StoryDraft): Sentence = when (draft.step) {
        CreationStep.Genre, CreationStep.Premise, CreationStep.Cast -> Sentence.Empty
        CreationStep.Twist, CreationStep.Starring, CreationStep.Tone, CreationStep.Setting ->
            sentences.story(draft, before = draft.step, ending = SentenceBuilder.Ending.Comma)
        CreationStep.Limits -> sentences.story(
            draft,
            before = CreationStep.Limits,
            ending = SentenceBuilder.Ending.Period,
        )
        CreationStep.Opening -> sentences.whole(draft)
    }

    /** One run of [writer] writing the current step, which started on [from]. */
    private class Writing(val writer: StepWriter, val from: StoryDraft) {
        lateinit var job: Job

        /** What it has written so far, shown in place of the draft. */
        var preview: StoryDraft? = null
    }

    /** What the last writing left for the step to offer, until the draft changes. */
    private sealed interface Aftermath {
        /** [after] was kept from writing that started on [before], and can be taken back to it. */
        data class Kept(val before: StoryDraft, val after: StoryDraft, val stopped: Boolean) : Aftermath

        data class Failed(val error: DraftingError) : Aftermath
    }

    companion object {
        /** Long enough to span a burst of keystrokes, short enough that little is lost if the app dies. */
        val TYPING_SETTLES: Duration = 400.milliseconds
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultStoryWizardFactory(
    private val repository: StoryDraftRepository,
    private val policies: Set<StepPolicy>,
    private val sentences: SentenceBuilder,
    private val ids: IdGenerator,
    private val drafter: ResolveDraftingModel,
) : StoryWizard.Factory {
    override fun create(scope: CoroutineScope): StoryWizard =
        DefaultStoryWizard(scope, repository, policies, sentences, ids, drafter)
}
