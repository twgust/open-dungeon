package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.core.model.Outcome
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
internal class DefaultStoryWizard(
    private val scope: CoroutineScope,
    private val repository: StoryDraftRepository,
    policies: Set<StepPolicy>,
    private val sentences: SentenceBuilder,
    private val ids: IdGenerator,
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

    init {
        scope.launch {
            try {
                for (action in actions) runIsolated(action)
            } finally {
                withContext(NonCancellable) { saveOnTheWayOut() }
            }
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
        val before = draft ?: return@enqueue
        val after = policyOf(before).edit(before, edit)?.takeIf { it != before } ?: return@enqueue
        show(after)
        settling?.cancel()
        settling = scope.launch {
            delay(typingSettles)
            enqueue(::saveTyping)
        }
    }

    override fun roll() = change { policy, draft ->
        if (policy.canRoll(draft)) policy.roll(draft)?.let(::settle) else null
    }

    override fun rollLine(line: CharacterLine) = change { policy, draft -> policy.rollLine(draft, line) }

    override fun next() = change { policy, draft -> if (policy.canAdvance(draft)) draft.forward() else null }

    override fun back() = enqueue {
        val current = draft
        val previous = current?.step?.previous
        if (current == null || previous == null) {
            // Leaving keeps the draft to resume, so whatever was typed is saved first.
            if (current != null) saveTyping()
            outcomeChannel.send(WizardOutcome.Left)
            return@enqueue
        }
        commit(before = current, after = current.copy(step = previous))
    }

    private fun enqueue(action: suspend () -> Unit) {
        actions.trySend(action)
    }

    /** Runs [transform] against the draft on screen and keeps the result when it changed anything. */
    private fun change(transform: suspend (StepPolicy, StoryDraft) -> StoryDraft?) = enqueue {
        val before = draft ?: return@enqueue
        val after = transform(policyOf(before), before)?.takeIf { it != before } ?: return@enqueue
        commit(before, after)
    }

    /** Shows [after] at once, then saves it; if the save fails, [before] comes back and the failure is reported. */
    private suspend fun commit(before: StoryDraft, after: StoryDraft) {
        show(after)
        val saved = save(after)
        if (saved is Outcome.Failure) {
            show(before)
            report(WizardFailure.Storage(saved.error))
        }
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
    private suspend fun runIsolated(action: suspend () -> Unit) {
        val draftBefore = draft
        val stateBefore = mutableState.value
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            draft = draftBefore
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

    /** A selection that completes its step moves on, unless the step still cannot be left. */
    private fun settle(selection: Selection): StoryDraft {
        val draft = selection.draft
        return if (selection.advance && policyOf(draft).canAdvance(draft)) draft.forward() else draft
    }

    private fun StoryDraft.forward(): StoryDraft = step.following?.let { copy(step = it) } ?: this

    private fun policyOf(draft: StoryDraft): StepPolicy = policies.getValue(draft.step)

    private fun stateOf(draft: StoryDraft): WizardState {
        val policy = policyOf(draft)
        return WizardState(
            draft = draft.id,
            step = draft.step,
            progress = (draft.step.ordinal + 1f) / CreationStep.entries.size,
            lead = leadOf(draft),
            body = policy.body(draft),
            controls = StepControls(
                canGoBack = true,
                // The last step's next is Begin, which only exists once a story can be committed.
                canAdvance = draft.step.following != null && policy.canAdvance(draft),
                advancesOnPick = policy.advancesOnPick,
                canRoll = policy.canRoll(draft),
                takesOwnWords = policy.takesOwnWords(draft),
            ),
        )
    }

    /**
     * The words above the step's prompt. On the sentence steps it is the story so far, left open for the
     * prompt to finish ("A gothic noir story," above "twisted by"); on the limits it is the whole story
     * clause, and on the first line it is the cast. The other steps lead with their prompt alone.
     */
    private fun leadOf(draft: StoryDraft): Sentence = when (draft.step) {
        CreationStep.Genre, CreationStep.Premise, CreationStep.Cast, CreationStep.FirstTurn -> Sentence.Empty
        CreationStep.Twist, CreationStep.Starring, CreationStep.Tone, CreationStep.Setting ->
            sentences.story(draft, before = draft.step, ending = SentenceBuilder.Ending.Comma)
        CreationStep.Limits -> sentences.story(draft, before = CreationStep.Limits, ending = SentenceBuilder.Ending.Period)
        CreationStep.FirstLine -> sentences.cast(draft)
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
) : StoryWizard.Factory {
    override fun create(scope: CoroutineScope): StoryWizard = DefaultStoryWizard(scope, repository, policies, sentences, ids)
}
