package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.CreationStep
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.Sentence
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.StoryDraftRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A thin coordinator: it opens the draft, hands each verb to the current step's [StepPolicy], and
 * saves what comes back. The draft in the repository is the only state, so anything else that edits
 * the draft shows up here too.
 */
internal class DefaultStoryWizard(
    private val scope: CoroutineScope,
    private val repository: StoryDraftRepository,
    policies: Set<StepPolicy>,
    private val sentences: SentenceBuilder,
    private val ids: IdGenerator,
) : StoryWizard {

    private val policies: Map<CreationStep, StepPolicy> = policies.associateBy(StepPolicy::step).also { byStep ->
        require(byStep.size == policies.size) { "More than one policy for a step" }
        require(byStep.keys == CreationStep.entries.toSet()) {
            "No policy for ${CreationStep.entries - byStep.keys}"
        }
    }

    private val opened = MutableStateFlow<DraftId?>(null)
    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val outcomeChannel = Channel<WizardOutcome>(Channel.BUFFERED)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: StateFlow<WizardState?> = opened
        .filterNotNull()
        .flatMapLatest(repository::observe)
        .filterNotNull()
        .map(::stateOf)
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    override val outcomes: Flow<WizardOutcome> = outcomeChannel.receiveAsFlow()

    init {
        scope.launch {
            for (action in actions) action()
        }
    }

    override fun start(draft: DraftId?) = enqueue {
        if (opened.value != null) return@enqueue
        val resumed = draft?.takeIf { repository.observe(it).first() != null } ?: repository.openDraft()?.id
        opened.value = resumed ?: StoryDraft(DraftId(ids.next())).also { repository.save(it) }.id
    }

    override fun select(choice: Choice) = change { policy, draft -> policy.select(draft, choice)?.let(::settle) }

    override fun submitOwn(text: String) = change { policy, draft -> policy.submitOwn(draft, text)?.let(::settle) }

    override fun edit(edit: DraftEdit) = change { policy, draft -> policy.edit(draft, edit) }

    override fun roll() = change { policy, draft ->
        if (policy.canRoll(draft)) policy.roll(draft)?.let(::settle) else null
    }

    override fun rollLine(line: CharacterLine) = change { policy, draft -> policy.rollLine(draft, line) }

    override fun next() = change { policy, draft -> if (policy.canAdvance(draft)) draft.forward() else null }

    override fun back() = change { _, draft ->
        val previous = draft.step.previous
        if (previous == null) outcomeChannel.send(WizardOutcome.Left)
        previous?.let { draft.copy(step = it) }
    }

    private fun enqueue(action: suspend () -> Unit) {
        actions.trySend(action)
    }

    /** Runs [transform] against the stored draft and saves the result when it changed anything. */
    private fun change(transform: suspend (StepPolicy, StoryDraft) -> StoryDraft?) = enqueue {
        val id = opened.value ?: return@enqueue
        val draft = repository.observe(id).first() ?: return@enqueue
        val changed = transform(policyOf(draft), draft)
        if (changed != null && changed != draft) repository.save(changed)
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
            sentence = sentenceOf(draft),
            body = policy.body(draft),
            controls = StepControls(
                canGoBack = true,
                // The last step's next is Begin, which only exists once a story can be committed.
                canAdvance = draft.step.following != null && policy.canAdvance(draft),
                canRoll = policy.canRoll(draft),
                takesOwnWords = policy.takesOwnWords(draft),
            ),
        )
    }

    /**
     * Up to the setting, the sentence grows with each pick and stays open. From the limits on it is
     * the whole story clause, closed, and from the cast on it names the people in it.
     */
    private fun sentenceOf(draft: StoryDraft): Sentence {
        val growing = draft.step < CreationStep.Limits
        val story = sentences.story(
            draft = draft,
            before = if (growing) CreationStep.entries[draft.step.ordinal + 1] else CreationStep.Limits,
            ending = if (growing) SentenceBuilder.Ending.None else SentenceBuilder.Ending.Period,
        )
        val cast = if (draft.step >= CreationStep.Cast) sentences.cast(draft) else Sentence.Empty
        return if (cast.isEmpty()) story else story + Sentence(listOf(Sentence.Segment.Fixed(" "))) + cast
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
