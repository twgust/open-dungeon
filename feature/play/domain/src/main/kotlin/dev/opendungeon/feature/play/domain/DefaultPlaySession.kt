package dev.opendungeon.feature.play.domain

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.BeatAsk
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.ResolveDraftingModel
import dev.opendungeon.domain.drafting.WriteBeat
import dev.opendungeon.domain.drafting.WritingProgress
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.Composer
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
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
 * A thin coordinator around the story: it opens it, decides what each verb may do to it, and keeps what
 * comes back.
 *
 * Verbs run one at a time on a single loop, which is the story's only writer while the session lives.
 * Each verb shows its result at once and then saves it; a save that fails takes the verb back and reports
 * why. Typing is the exception: text is shown as typed and saved once it settles, so a burst of keystrokes
 * is one write, and text that could not be saved stays on screen for the next save. Whatever is still
 * unsaved when the session's scope ends is saved on the way out.
 *
 * A model writing a beat is the other exception, since writing takes seconds and the player must be able
 * to stop it. The writing runs beside the loop and hands each piece back to it. While it runs, the screen
 * shows the story as the request left it, the turn sent and the direction used, but only a result is
 * saved: failing or leaving puts back the story as it was, turn and direction included. Stopping keeps
 * what was written so far. Whatever a model wrote, and every edit, can be taken back until the story
 * changes again.
 */
@Suppress("TooManyFunctions") // The verbs of PlaySession; everything else here is private.
internal class DefaultPlaySession(
    private val scope: CoroutineScope,
    private val repository: StoryRepository,
    private val writeBeat: WriteBeat,
    private val ids: IdGenerator,
    drafter: ResolveDraftingModel,
    private val typingSettles: Duration = TYPING_SETTLES,
) : PlaySession {

    private val mutableState = MutableStateFlow<Play>(Play.Opening)
    override val state: StateFlow<Play> = mutableState.asStateFlow()

    private val outcomeChannel = Channel<PlayOutcome>(Channel.BUFFERED)
    override val outcomes: Flow<PlayOutcome> = outcomeChannel.receiveAsFlow()

    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    // Read and written by the action loop only, so they need no locking.

    /** The story on screen. While a model writes, the story as it was before the request. */
    private var story: Story? = null

    /** The story as last written to the repository. */
    private var stored: Story? = null

    /** Saves typed text once typing pauses. */
    private var settling: Job? = null

    /** The model that writes, once known; `null` when none is chosen. */
    private var drafting: DraftingModel? = null

    /** The model writing a beat, if one is. */
    private var writing: Run? = null

    /** What the last change left for the line above the bar: the way back from it, or its failure. */
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
                    // A failing source is a bug below; play goes on without a model rather than die.
                    if (cause is CancellationException || cause !is Exception) throw cause
                    enqueue {
                        drafting = null
                        report(PlayFailure.Unexpected(cause))
                    }
                }
                .collect { model -> enqueue { drafting = model } }
        }
    }

    override fun start(story: StoryId) = enqueue {
        if (this.story != null) return@enqueue
        mutableState.value = Play.Opening
        when (val read = repository.story(story)) {
            is Outcome.Failure -> mutableState.value = Play.Unreadable(read.error)
            is Outcome.Success -> {
                val opened = read.value
                if (opened == null) {
                    mutableState.value = Play.Missing
                } else {
                    stored = opened
                    show(opened)
                }
            }
        }
    }

    override fun typeTurn(text: String) = type { current ->
        current.copy(unsent = current.unsent.copy(turn = text.take(Story.MAX_TURN_LENGTH)))
    }

    override fun advance() = enqueue {
        val current = idle()?.takeIf { it.unsent.composer == null } ?: return@enqueue
        next(current)
    }

    override fun retry() = enqueue {
        val current = idle()?.takeIf { it.unsent.composer == null && it.endsOnNarration() } ?: return@enqueue
        again(current, reason = null)
    }

    override fun openGuide() = enqueue {
        val current = idle()?.takeIf { it.unsent.composer !is Composer.Guide } ?: return@enqueue
        place(current.withComposer(Composer.Guide(current.guide.orEmpty())))
    }

    override fun openReason() = enqueue {
        val current = idle()?.takeIf { it.unsent.composer == null && it.endsOnNarration() } ?: return@enqueue
        place(current.withComposer(Composer.Reason("")))
    }

    override fun editBeat(beat: BeatId) = enqueue {
        val current = story ?: return@enqueue
        if (writing != null) return@enqueue refuse(Refusal.StopFirst)
        val open = current.unsent.composer
        if (open is Composer.Edit && open.beat == beat) return@enqueue
        if (open is Composer.Edit && current.editChanges(open)) return@enqueue refuse(Refusal.FinishEditFirst)
        val target = current.beats.firstOrNull { it.id == beat } ?: return@enqueue
        place(current.withComposer(Composer.Edit(beat, target.text)))
    }

    override fun typeNote(text: String) = type { current ->
        when (val open = current.unsent.composer) {
            null -> null
            is Composer.Guide -> current.withComposer(open.copy(text = text.take(Story.MAX_NOTE_LENGTH)))
            is Composer.Reason -> current.withComposer(open.copy(text = text.take(Story.MAX_NOTE_LENGTH)))
            is Composer.Edit -> current.withComposer(open.copy(text = text.take(Story.MAX_BEAT_LENGTH)))
        }
    }

    override fun submit() = enqueue {
        val current = idle() ?: return@enqueue
        when (val open = current.unsent.composer) {
            null -> Unit
            is Composer.Guide -> {
                val guide = open.text.trim().takeIf { canKeepGuide(current, it) } ?: return@enqueue
                commit(before = current, after = current.copy(guide = guide).withComposer(null))
            }
            is Composer.Reason -> {
                val reason = open.text.trim().takeIf { it.isNotEmpty() && current.endsOnNarration() } ?: return@enqueue
                again(current, reason)
            }
            is Composer.Edit -> saveEdit(current, open)
        }
    }

    override fun cancel() = enqueue {
        val current = idle()?.takeIf { it.unsent.composer != null } ?: return@enqueue
        place(current.withComposer(null))
    }

    override fun removeGuide() = enqueue {
        val current = idle()?.takeIf { it.guide != null } ?: return@enqueue
        commit(before = current, after = current.copy(guide = null))
    }

    override fun stop() = enqueue {
        val run = writing ?: return@enqueue
        stopWriting()
        val kept = run.preview
        if (kept == null) {
            render()
        } else {
            val after = run.shown.withBeat(run.ask, kept)
            commit(before = run.from, after = after, aftermath = keptBy(run, after, Status.Change.Stopped))
        }
    }

    override fun undo() = enqueue {
        val kept = aftermath as? Aftermath.Kept ?: return@enqueue
        val current = idle()?.takeIf { it == kept.after } ?: return@enqueue
        commit(before = current, after = kept.undo)
    }

    override fun tryAgain() = enqueue {
        val failed = aftermath as? Aftermath.Failed ?: return@enqueue
        val current = idle() ?: return@enqueue
        when (val ask = failed.ask) {
            is BeatAsk.Next -> next(current)
            is BeatAsk.Again -> if (current.endsOnNarration()) again(current, ask.reason)
        }
    }

    override fun dismissFailure() = enqueue {
        if (aftermath !is Aftermath.Failed) return@enqueue
        aftermath = null
        render()
    }

    override fun back() = enqueue {
        if (story == null) return@enqueue outcomeChannel.send(PlayOutcome.Left)
        if (writing != null) {
            confirmingLeave = true
            render()
        } else {
            leave()
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
                leave()
            }
        }
    }

    /** Leaving keeps everything, so whatever was typed is saved first. */
    private suspend fun leave() {
        saveTyping()
        outcomeChannel.send(PlayOutcome.Left)
    }

    /** The beat after the last one, after the player's turn when they typed one, with their direction. */
    private suspend fun next(current: Story) {
        val turn = current.unsent.turn.trim()
        val beats = if (turn.isEmpty()) {
            current.beats
        } else {
            current.beats +
                Beat(BeatId(ids.next()), Beat.Kind.Turn, turn)
        }
        val shown = current.copy(beats = beats, guide = null, unsent = current.unsent.copy(turn = ""))
        startWriting(BeatAsk.Next(guide = current.guide), from = current, shown = shown)
    }

    /** The last beat again, for [reason] when the player gave one, with their direction. */
    private suspend fun again(current: Story, reason: String?) {
        val shown = current.copy(guide = null).withComposer(null)
        startWriting(BeatAsk.Again(guide = current.guide, reason = reason), from = current, shown = shown)
    }

    private suspend fun saveEdit(current: Story, edit: Composer.Edit) {
        if (!current.editChanges(edit) || !current.canSave(edit)) return
        val text = edit.text.trim()
        val closed = current.withComposer(null)
        val after = if (text.isEmpty()) {
            closed.copy(beats = current.beats.filterNot { it.id == edit.beat })
        } else {
            closed.copy(beats = current.beats.map { if (it.id == edit.beat) it.copy(text = text) else it })
        }
        val change = if (text.isEmpty()) Status.Change.BeatDeleted else Status.Change.EditSaved
        commit(
            before = current,
            after = after,
            aftermath = Aftermath.Kept(undo = closed, after = after, change = change),
        )
    }

    private fun enqueue(action: suspend () -> Unit) {
        actions.trySend(action)
    }

    /** The story on screen, when nothing is writing it. */
    private fun idle(): Story? = story?.takeIf { writing == null }

    /**
     * Shows what typing made of the story at once and saves it once typing settles. Typing waits while a
     * model writes, and puts away whatever the last change left on the line above the bar.
     */
    private fun type(transform: (Story) -> Story?) = enqueue {
        val before = idle() ?: return@enqueue
        val after = transform(before)?.takeIf { it != before } ?: return@enqueue
        aftermath = null
        place(after)
    }

    /** Shows [after] and saves it once typing settles, for changes too small to take back, like opening a composer. */
    private fun place(after: Story) {
        if (after.unsent.composer != story?.unsent?.composer) aftermath = aftermath.takeIf { it is Aftermath.Failed }
        show(after)
        settling?.cancel()
        settling = scope.launch {
            delay(typingSettles)
            enqueue(::saveTyping)
        }
    }

    /**
     * Shows [after] at once, then saves it; if the save fails, [before] comes back, with what the last
     * change left, and the failure is reported. Any change replaces what the last change left with
     * [aftermath], which is nothing unless the change can be taken back.
     */
    private suspend fun commit(before: Story, after: Story, aftermath: Aftermath? = null) {
        val aftermathBefore = this.aftermath
        this.aftermath = aftermath
        show(after)
        val saved = save(after)
        if (saved is Outcome.Failure) {
            this.aftermath = aftermathBefore
            show(before)
            report(PlayFailure.Storage(saved.error))
        }
    }

    /**
     * Writes a beat beside the loop. Each piece comes back through the loop, tagged with this run, so a
     * piece that arrives after the run was stopped, or replaced, is dropped.
     */
    private suspend fun startWriting(ask: BeatAsk, from: Story, shown: Story) {
        val model = drafting
        if (model == null) {
            aftermath = Aftermath.Failed(ask, DraftingError.NoModel)
            render()
            return
        }
        // What was typed before asking is kept even if the request fails.
        saveTyping()
        val run = Run(ask, from, shown)
        run.job = scope.launch(start = CoroutineStart.LAZY) { write(run, model) }
        writing = run
        aftermath = null
        render()
        run.job.start()
    }

    /** Runs off the loop: it only hands pieces to it. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun write(run: Run, model: DraftingModel) {
        try {
            writeBeat(run.shown, run.ask, model).collect { progress -> enqueue { onWritten(run, progress) } }
            // Reached only when the writer ended without its last word, which no writer should do.
            enqueue { endUnexpectedly(run, IllegalStateException("The beat writer ended without a result")) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // As in [runIsolated]: a writer that throws has a bug, and play outlives it.
            enqueue { endUnexpectedly(run, e) }
        }
    }

    private suspend fun onWritten(run: Run, progress: WritingProgress) {
        if (writing !== run) return
        when (progress) {
            is WritingProgress.Writing -> {
                run.preview = progress.text
                render()
            }
            is WritingProgress.Written -> {
                endWriting()
                val after = run.shown.withBeat(run.ask, progress.text)
                val change = if (run.ask is BeatAsk.Again) Status.Change.Rewritten else Status.Change.Added
                commit(before = run.from, after = after, aftermath = keptBy(run, after, change))
            }
            is WritingProgress.Failed -> {
                endWriting()
                aftermath = Aftermath.Failed(run.ask, progress.error)
                render()
            }
        }
    }

    /** Taking back what a run kept returns the story to how it was before the request, its composer closed. */
    private fun keptBy(run: Run, after: Story, change: Status.Change) =
        Aftermath.Kept(undo = run.from.withComposer(null), after = after, change = change)

    private suspend fun endUnexpectedly(run: Run, cause: Exception) {
        if (writing !== run) return
        endWriting()
        render()
        report(PlayFailure.Unexpected(cause))
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
        val current = story ?: return
        val saved = save(current)
        if (saved is Outcome.Failure) report(PlayFailure.Storage(saved.error))
    }

    private suspend fun save(story: Story): Outcome<Unit, StorageError> {
        settling?.cancel()
        if (story == stored) return Outcome.Success(Unit)
        return repository.save(story).also { if (it is Outcome.Success) stored = story }
    }

    /** Nobody is listening once the scope has ended, so a failure here can only be dropped. */
    private suspend fun saveOnTheWayOut() {
        val current = story ?: return
        if (current != stored) repository.save(current)
    }

    /**
     * The loop is the session's boundary, so one verb that throws must not end every verb after it. A throw
     * here is a bug in a rule, never an expected failure (those come back typed), so it is reported as
     * [PlayFailure.Unexpected] with its cause, and the verb is taken back.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runIsolated(action: suspend () -> Unit) {
        val storyBefore = story
        val stateBefore = mutableState.value
        val writingBefore = writing
        val aftermathBefore = aftermath
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Writing the verb started is stopped with it, so the screen is never left waiting on nothing.
            if (writing != null && writing !== writingBefore) stopWriting()
            story = storyBefore
            aftermath = aftermathBefore
            mutableState.value = stateBefore
            report(PlayFailure.Unexpected(e))
        }
    }

    private suspend fun report(failure: PlayFailure) {
        outcomeChannel.send(PlayOutcome.Failed(failure))
    }

    private suspend fun refuse(reason: Refusal) {
        outcomeChannel.send(PlayOutcome.Refused(reason))
    }

    /** Builds the state before taking the story, so a state that fails to build changes nothing. */
    private fun show(story: Story) {
        mutableState.value = Play.Playing(stateOf(story))
        this.story = story
    }

    /** Shows the story again, for a change around it: writing, a question, a failure put away. */
    private fun render() {
        story?.let(::show)
    }

    private fun stateOf(story: Story): PlayState {
        val run = writing
        val shown = run?.shown ?: story
        val open = shown.unsent.composer
        return PlayState(
            story = story.id,
            beats = beatsOf(shown, run),
            writing = run?.let { Writing(again = it.ask is BeatAsk.Again, text = it.preview) },
            turn = TurnField(text = shown.unsent.turn, enabled = run == null, maxLength = Story.MAX_TURN_LENGTH),
            guide = shown.guide?.takeIf { open == null },
            composer = open?.let(::composerOf),
            retryOffered = run == null && open == null && story.endsOnNarration(),
            status = statusOf(story, run),
            secondary = if (open != null && run == null) {
                Secondary.Cancel
            } else {
                Secondary.Guide(replacing = shown.guide != null, enabled = run == null)
            },
            primary = primaryOf(story, run),
            confirmingLeave = confirmingLeave,
        )
    }

    private fun beatsOf(shown: Story, run: Run?): List<PlayBeat> {
        val open = shown.unsent.composer
        val last = shown.beats.lastIndex
        return shown.beats.mapIndexed { index, beat ->
            val look = when {
                run?.ask is BeatAsk.Again && index == last -> PlayBeat.Look.Rewritten
                open is Composer.Edit -> if (beat.id == open.beat) PlayBeat.Look.Editing else PlayBeat.Look.Faded
                open is Composer.Reason && index != last -> PlayBeat.Look.Faded
                else -> PlayBeat.Look.Plain
            }
            PlayBeat(beat.id, beat.kind, beat.text, look)
        }
    }

    private fun composerOf(open: Composer): ComposerState = when (open) {
        is Composer.Guide -> ComposerState(ComposerState.Kind.Guide, open.text, Story.MAX_NOTE_LENGTH)
        is Composer.Reason -> ComposerState(ComposerState.Kind.Reason, open.text, Story.MAX_NOTE_LENGTH)
        is Composer.Edit -> ComposerState(ComposerState.Kind.Edit, open.text, Story.MAX_BEAT_LENGTH)
    }

    private fun statusOf(story: Story, run: Run?): Status? {
        if (run != null) return Status.Writing(again = run.ask is BeatAsk.Again)
        return when (val left = aftermath) {
            null -> null
            is Aftermath.Failed -> Status.Failed(
                again = left.ask is BeatAsk.Again,
                error = left.error,
                fix = fixFor(left.error),
            )
            // The way back shows while the story is as the change left it and no composer covers the line.
            is Aftermath.Kept -> Status.Changed(left.change).takeIf {
                story == left.after &&
                    story.unsent.composer == null
            }
        }
    }

    private fun fixFor(error: DraftingError): Status.Fix = when {
        error == DraftingError.NoModel -> Status.Fix.ChooseModel
        error is DraftingError.Generation && error.error == GenerationError.NoProvider -> Status.Fix.ChooseModel
        else -> Status.Fix.TryAgain
    }

    private fun primaryOf(story: Story, run: Run?): Primary {
        if (run != null) return Primary.Stop
        return when (val open = story.unsent.composer) {
            null -> Primary.Advance(sends = story.unsent.turn.isNotBlank())
            is Composer.Guide -> Primary.Submit(
                if (story.guide == null) SubmitAction.AddGuide else SubmitAction.UpdateGuide,
                enabled = canKeepGuide(story, open.text.trim()),
            )
            is Composer.Reason -> Primary.Submit(
                SubmitAction.Retry,
                enabled = open.text.isNotBlank() && story.endsOnNarration(),
            )
            is Composer.Edit -> Primary.Submit(
                if (open.text.isBlank()) SubmitAction.DeleteBeat else SubmitAction.Save,
                enabled = story.editChanges(open) && story.canSave(open),
            )
        }
    }

    private fun canKeepGuide(story: Story, guide: String): Boolean = guide.isNotEmpty() && guide != story.guide

    private fun Story.endsOnNarration(): Boolean = beats.last().kind == Beat.Kind.Narration

    private fun Story.withComposer(composer: Composer?): Story = copy(unsent = unsent.copy(composer = composer))

    /** Whether keeping [edit] would change its beat. Spaces around the text are not a change. */
    private fun Story.editChanges(edit: Composer.Edit): Boolean =
        beats.firstOrNull { it.id == edit.beat }?.let { it.text != edit.text.trim() } ?: false

    /** An emptied beat is removed, except the only one: a story always has a beat. */
    private fun Story.canSave(edit: Composer.Edit): Boolean = edit.text.isNotBlank() || beats.size > 1

    /** The story with what a model wrote for [ask]: a new beat after the last, or the last beat written again. */
    private fun Story.withBeat(ask: BeatAsk, text: String): Story = when (ask) {
        is BeatAsk.Next -> copy(beats = beats + Beat(BeatId(ids.next()), Beat.Kind.Narration, text))
        is BeatAsk.Again -> copy(beats = beats.dropLast(1) + beats.last().copy(text = text))
    }

    /** One run of a model writing [ask], which started on [from] and shows [shown] while it writes. */
    private class Run(val ask: BeatAsk, val from: Story, val shown: Story) {
        lateinit var job: Job

        /** What it has written so far. */
        var preview: String? = null
    }

    /** What the last change left for the line above the bar, until the story changes. */
    private sealed interface Aftermath {
        /** [after] was kept by a change that can be taken back to [undo]. */
        data class Kept(val undo: Story, val after: Story, val change: Status.Change) : Aftermath

        data class Failed(val ask: BeatAsk, val error: DraftingError) : Aftermath
    }

    companion object {
        /** Long enough to span a burst of keystrokes, short enough that little is lost if the app dies. */
        val TYPING_SETTLES: Duration = 400.milliseconds
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultPlaySessionFactory(
    private val repository: StoryRepository,
    private val writeBeat: WriteBeat,
    private val ids: IdGenerator,
    private val drafter: ResolveDraftingModel,
) : PlaySession.Factory {
    override fun create(scope: CoroutineScope): PlaySession =
        DefaultPlaySession(scope, repository, writeBeat, ids, drafter)
}
