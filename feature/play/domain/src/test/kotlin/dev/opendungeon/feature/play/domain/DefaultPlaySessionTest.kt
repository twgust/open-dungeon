package dev.opendungeon.feature.play.domain

import app.cash.turbine.test
import dev.opendungeon.domain.drafting.BeatAsk
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.BeatId
import dev.opendungeon.domain.story.Composer
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StorySetup
import dev.opendungeon.domain.story.Unsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Play's rules: opening a story, writing beats, notes, edits, stopping, undo, failures and leaving. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPlaySessionTest {

    private val beats = ScriptedBeats()
    private val drafter = FakeDrafter()

    private fun test(
        story: Story = salt,
        open: StoryId? = salt.id,
        block: suspend TestScope.(PlaySession, InMemoryStories) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryStories(story)
        val session = DefaultPlaySession(backgroundScope, repository, beats, CountingIds("new"), drafter)
        if (open != null) session.start(open)
        block(session, repository)
    }

    private val PlaySession.playing: PlayState get() = assertIs<Play.Playing>(state.value).state

    private val PlayState.texts: List<String> get() = beats.map(PlayBeat::text)

    // Opening

    @Test
    fun `opens the story with its beats and offers Continue`() = test { session, _ ->
        val state = session.playing
        assertEquals(salt.beats.map(Beat::text), state.texts)
        assertEquals(Primary.Advance(sends = false), state.primary)
        assertEquals(Secondary.Guide(replacing = false, enabled = true), state.secondary)
        assertTrue(state.retryOffered)
        assertTrue(state.turn.enabled)
        assertNull(state.status)
    }

    @Test
    fun `a story that is gone says so`() = test(open = null) { session, _ ->
        session.start(StoryId("gone"))
        assertEquals(Play.Missing, session.state.value)
    }

    @Test
    fun `a story that cannot be read can be opened again`() = test(open = null) { session, repository ->
        repository.failReads = 1
        session.start(salt.id)
        assertEquals(Play.Unreadable(StorageError.Full), session.state.value)

        session.start(salt.id)
        assertEquals(salt.beats.size, session.playing.beats.size)
    }

    @Test
    fun `opening again once open changes nothing`() = test { session, repository ->
        session.typeTurn("I wait.")
        repository.byId.value += salt.id to salt.copy(beats = listOf(opening))
        session.start(salt.id)
        assertEquals("I wait.", session.playing.turn.text)
        assertEquals(3, session.playing.beats.size)
    }

    @Test
    fun `retry is offered only after a beat a model wrote`() = test(salt.copy(beats = listOf(opening, turn))) {
        session,
        _,
        ->
        assertFalse(session.playing.retryOffered)
        session.retry()
        session.openReason()
        assertTrue(beats.runs.isEmpty())
        assertNull(session.playing.composer)
    }

    // Continue and Send

    @Test
    fun `continue writes the next beat, streaming it, and keeps it`() = test { session, repository ->
        session.advance()
        val asked = session.playing
        assertEquals(Writing(again = false, text = null), asked.writing)
        assertEquals(Primary.Stop, asked.primary)
        assertEquals(Status.Writing(again = false), asked.status)
        assertFalse(asked.turn.enabled)
        assertFalse(asked.retryOffered)
        assertEquals(Secondary.Guide(replacing = false, enabled = false), asked.secondary)
        assertEquals(BeatAsk.Next(guide = null), beats.last.ask)

        beats.last.writing("The key")
        assertEquals("The key", session.playing.writing?.text)

        beats.last.written("The key is cold now.")
        val done = session.playing
        assertNull(done.writing)
        assertEquals("The key is cold now.", done.texts.last())
        assertEquals(Beat.Kind.Narration, done.beats.last().kind)
        assertEquals(Status.Changed(Status.Change.Added), done.status)
        assertEquals(done.texts, repository.stored().beats.map(Beat::text))
    }

    @Test
    fun `send puts the turn in the story at once and clears the field`() = test { session, repository ->
        session.typeTurn("  “And if I don’t go?”  ")
        assertEquals(Primary.Advance(sends = true), session.playing.primary)

        session.advance()
        val asked = session.playing
        assertEquals("“And if I don’t go?”", asked.texts.last())
        assertEquals(Beat.Kind.Turn, asked.beats.last().kind)
        assertEquals("", asked.turn.text)
        assertEquals("“And if I don’t go?”", beats.last.story.beats.last().text)

        beats.last.written("Then don’t.")
        assertEquals(listOf("“And if I don’t go?”", "Then don’t."), session.playing.texts.takeLast(2))
        assertEquals("", repository.stored().unsent.turn)
    }

    @Test
    fun `a turn of only spaces sends nothing but asks for the next beat`() = test { session, _ ->
        session.typeTurn("   ")
        assertEquals(Primary.Advance(sends = false), session.playing.primary)
        session.advance()
        assertEquals(salt.beats.size, beats.last.story.beats.size)
    }

    @Test
    fun `a double tap on Continue writes one beat`() = test { session, _ ->
        session.advance()
        session.advance()
        session.retry()
        assertEquals(1, beats.runs.size)
    }

    @Test
    fun `a failed beat puts the turn back, changes nothing, and can be asked again`() = test { session, repository ->
        session.typeTurn("I run.")
        session.advance()
        beats.last.failed(DraftingError.Generation(GenerationError.Interrupted))

        val failed = session.playing
        assertEquals(salt.beats.map(Beat::text), failed.texts)
        assertEquals("I run.", failed.turn.text)
        assertEquals(
            Status.Failed(again = false, DraftingError.Generation(GenerationError.Interrupted), Status.Fix.TryAgain),
            failed.status,
        )
        assertEquals(salt.beats, repository.stored().beats)

        session.tryAgain()
        assertEquals("I run.", beats.last.story.beats.last().text)
        beats.last.written("You run.")
        assertEquals(listOf("I run.", "You run."), session.playing.texts.takeLast(2))
    }

    @Test
    fun `with no model, asking fails at once and offers to choose one`() = test { session, _ ->
        drafter.model.value = null
        session.typeTurn("I run.")
        session.advance()
        val failed = session.playing
        assertTrue(beats.runs.isEmpty())
        assertEquals(Status.Failed(again = false, DraftingError.NoModel, Status.Fix.ChooseModel), failed.status)
        assertEquals("I run.", failed.turn.text)
        assertNull(failed.writing)
    }

    @Test
    fun `no provider for the settings offers another model`() = test { session, _ ->
        session.advance()
        beats.last.failed(DraftingError.Generation(GenerationError.NoProvider))
        assertEquals(Status.Fix.ChooseModel, assertIs<Status.Failed>(session.playing.status).fix)
    }

    @Test
    fun `dismissing a failure leaves the story as it is`() = test { session, _ ->
        session.advance()
        beats.last.failed(DraftingError.Unusable)
        session.dismissFailure()
        assertNull(session.playing.status)
        session.tryAgain()
        assertEquals(1, beats.runs.size)
    }

    @Test
    fun `a beat that cannot be saved is taken back and reported`() = test { session, repository ->
        session.typeTurn("I run.")
        session.outcomes.test {
            session.advance()
            repository.failSaves = 1
            beats.last.written("You run.")
            assertEquals(PlayOutcome.Failed(PlayFailure.Storage(StorageError.Full)), awaitItem())
        }
        val state = session.playing
        assertEquals(salt.beats.map(Beat::text), state.texts)
        assertEquals("I run.", state.turn.text)
        assertNull(state.status)
    }

    // Retry

    @Test
    fun `retry writes the last beat again in its place`() = test { session, repository ->
        session.retry()
        val asked = session.playing
        assertEquals(PlayBeat.Look.Rewritten, asked.beats.last().look)
        assertEquals(Writing(again = true, text = null), asked.writing)
        assertEquals(Status.Writing(again = true), asked.status)
        assertEquals(BeatAsk.Again(guide = null, reason = null), beats.last.ask)

        beats.last.written("Maren laughs once.")
        val done = session.playing
        assertEquals(listOf(opening.text, turn.text, "Maren laughs once."), done.texts)
        assertEquals(narration.id, done.beats.last().id)
        assertEquals(Status.Changed(Status.Change.Rewritten), done.status)
        assertEquals("Maren laughs once.", repository.stored().beats.last().text)
    }

    @Test
    fun `retry with a reason waits for the reason and passes it on`() = test { session, _ ->
        session.openReason()
        val open = session.playing
        assertEquals(ComposerState(ComposerState.Kind.Reason, "", Story.MAX_NOTE_LENGTH), open.composer)
        assertEquals(Primary.Submit(SubmitAction.Retry, enabled = false), open.primary)
        assertEquals(Secondary.Cancel, open.secondary)
        assertEquals(
            listOf(PlayBeat.Look.Faded, PlayBeat.Look.Faded, PlayBeat.Look.Plain),
            open.beats.map(PlayBeat::look),
        )
        assertFalse(open.retryOffered)

        session.typeNote("   ")
        session.submit()
        assertTrue(beats.runs.isEmpty())

        session.typeNote(" Too neat. ")
        assertEquals(Primary.Submit(SubmitAction.Retry, enabled = true), session.playing.primary)
        session.submit()
        assertEquals(BeatAsk.Again(guide = null, reason = "Too neat."), beats.last.ask)
        assertNull(session.playing.composer)
    }

    @Test
    fun `a failed retry with a reason can be asked again with the same reason`() = test { session, _ ->
        session.openReason()
        session.typeNote("Too neat.")
        session.submit()
        beats.last.failed(DraftingError.Unusable)
        assertEquals(Status.Failed(again = true, DraftingError.Unusable, Status.Fix.TryAgain), session.playing.status)
        assertEquals(narration.text, session.playing.texts.last())

        session.tryAgain()
        assertEquals(BeatAsk.Again(guide = null, reason = "Too neat."), beats.last.ask)
    }

    // Guide

    @Test
    fun `a guide is added, shown, used once by the next beat, then cleared`() = test { session, repository ->
        session.openGuide()
        assertEquals(Primary.Submit(SubmitAction.AddGuide, enabled = false), session.playing.primary)
        session.typeNote(" Maren lies. ")
        session.submit()
        val guided = session.playing
        assertEquals("Maren lies.", guided.guide)
        assertNull(guided.composer)
        assertEquals(Secondary.Guide(replacing = true, enabled = true), guided.secondary)
        assertEquals("Maren lies.", repository.stored().guide)

        session.advance()
        assertEquals(BeatAsk.Next(guide = "Maren lies."), beats.last.ask)
        assertNull(session.playing.guide)
        beats.last.written("Forty coins, not thirty.")
        assertNull(repository.stored().guide)
    }

    @Test
    fun `a guide also steers Retry`() = test(salt.copy(guide = "Keep it in the rain.")) { session, _ ->
        session.retry()
        assertEquals(BeatAsk.Again(guide = "Keep it in the rain.", reason = null), beats.last.ask)
    }

    @Test
    fun `a failed beat keeps the guide for the next try`() = test(salt.copy(guide = "Maren lies.")) {
        session,
        repository,
        ->
        session.advance()
        beats.last.failed(DraftingError.Unusable)
        assertEquals("Maren lies.", session.playing.guide)
        assertEquals("Maren lies.", repository.stored().guide)
    }

    @Test
    fun `editing a guide opens with its words and saves only a change`() = test(salt.copy(guide = "Maren lies.")) {
        session,
        _,
        ->
        session.openGuide()
        val open = session.playing
        assertEquals("Maren lies.", open.composer?.text)
        assertNull(open.guide)
        assertEquals(Primary.Submit(SubmitAction.UpdateGuide, enabled = false), open.primary)

        session.typeNote("Maren tells the truth.")
        session.submit()
        assertEquals("Maren tells the truth.", session.playing.guide)
    }

    @Test
    fun `a guide can be taken back`() = test(salt.copy(guide = "Maren lies.")) { session, repository ->
        session.removeGuide()
        assertNull(session.playing.guide)
        assertNull(repository.stored().guide)
    }

    @Test
    fun `cancel closes a note and keeps nothing of it`() = test { session, _ ->
        session.openGuide()
        session.typeNote("Maren lies.")
        session.cancel()
        val state = session.playing
        assertNull(state.composer)
        assertNull(state.guide)
        assertEquals(Primary.Advance(sends = false), state.primary)
    }

    // Edit

    @Test
    fun `tapping a beat opens it in place and saves the edit`() = test { session, repository ->
        session.editBeat(turn.id)
        val open = session.playing
        assertEquals(ComposerState(ComposerState.Kind.Edit, turn.text, Story.MAX_BEAT_LENGTH), open.composer)
        assertEquals(
            listOf(PlayBeat.Look.Faded, PlayBeat.Look.Editing, PlayBeat.Look.Faded),
            open.beats.map(PlayBeat::look),
        )
        assertEquals(Primary.Submit(SubmitAction.Save, enabled = false), open.primary)

        session.typeNote("  You take the key.  ")
        session.submit()
        val saved = session.playing
        assertEquals("You take the key.", saved.texts[1])
        assertEquals(Status.Changed(Status.Change.EditSaved), saved.status)
        assertEquals("You take the key.", repository.stored().beats[1].text)

        session.undo()
        assertEquals(turn.text, session.playing.texts[1])
        assertEquals(turn.text, repository.stored().beats[1].text)
    }

    @Test
    fun `emptying a beat deletes it, and undo brings it back`() = test { session, _ ->
        session.editBeat(turn.id)
        session.typeNote("  ")
        assertEquals(Primary.Submit(SubmitAction.DeleteBeat, enabled = true), session.playing.primary)
        session.submit()
        assertEquals(listOf(opening.text, narration.text), session.playing.texts)
        assertEquals(Status.Changed(Status.Change.BeatDeleted), session.playing.status)

        session.undo()
        assertEquals(salt.beats.map(Beat::text), session.playing.texts)
    }

    @Test
    fun `the only beat cannot be deleted`() = test(salt.copy(beats = listOf(opening))) { session, _ ->
        session.editBeat(opening.id)
        session.typeNote("")
        assertEquals(Primary.Submit(SubmitAction.DeleteBeat, enabled = false), session.playing.primary)
        session.submit()
        assertEquals(listOf(opening.text), session.playing.texts)
    }

    @Test
    fun `another beat waits while an edit has changes, but not while it has none`() = test { session, _ ->
        session.editBeat(turn.id)
        session.editBeat(opening.id)
        assertEquals(Composer.Edit(opening.id, opening.text).text, session.playing.composer?.text)

        session.typeNote("Snow falls.")
        session.outcomes.test {
            session.editBeat(narration.id)
            assertEquals(PlayOutcome.Refused(Refusal.FinishEditFirst), awaitItem())
        }
        assertEquals("Snow falls.", session.playing.composer?.text)
    }

    @Test
    fun `beats cannot be edited while a model writes`() = test { session, _ ->
        session.advance()
        session.outcomes.test {
            session.editBeat(turn.id)
            assertEquals(PlayOutcome.Refused(Refusal.StopFirst), awaitItem())
        }
        assertNull(session.playing.composer)
    }

    // Stop and undo

    @Test
    fun `stop keeps what was written, and undo takes it back`() = test { session, repository ->
        session.typeTurn("I wait.")
        session.advance()
        beats.last.writing("You hear her boots")
        session.stop()

        val stopped = session.playing
        assertTrue(beats.last.cancelled)
        assertEquals("You hear her boots", stopped.texts.last())
        assertEquals(Status.Changed(Status.Change.Stopped), stopped.status)
        assertEquals("You hear her boots", repository.stored().beats.last().text)

        session.undo()
        val undone = session.playing
        assertEquals(salt.beats.map(Beat::text), undone.texts)
        assertEquals("I wait.", undone.turn.text)
        assertNull(undone.status)
    }

    @Test
    fun `stop before the first words puts everything back`() = test { session, _ ->
        session.typeTurn("I wait.")
        session.advance()
        session.stop()
        val state = session.playing
        assertEquals(salt.beats.map(Beat::text), state.texts)
        assertEquals("I wait.", state.turn.text)
        assertNull(state.writing)
    }

    @Test
    fun `words that arrive after stopping are dropped`() = test { session, _ ->
        session.advance()
        val run = beats.last
        run.writing("Late")
        session.stop()
        run.written("Late words")
        assertEquals("Late", session.playing.texts.last())
    }

    @Test
    fun `undo is offered only while the story is as the change left it`() = test { session, _ ->
        session.advance()
        beats.last.written("The key is cold.")
        session.typeTurn("I pocket it.")
        assertNull(session.playing.status)
        session.undo()
        assertEquals("The key is cold.", session.playing.texts.last())
    }

    // Typing

    @Test
    fun `typing is saved once it settles`() = test { session, repository ->
        session.typeTurn("I")
        session.typeTurn("I wait")
        assertTrue(repository.saved.isEmpty())
        advanceTimeBy(DefaultPlaySession.TYPING_SETTLES)
        runCurrent()
        assertEquals(listOf("I wait"), repository.saved.map { it.unsent.turn })
    }

    @Test
    fun `a turn longer than allowed is cut`() = test { session, _ ->
        session.typeTurn("a".repeat(Story.MAX_TURN_LENGTH + 10))
        assertEquals(Story.MAX_TURN_LENGTH, session.playing.turn.text.length)
    }

    @Test
    fun `typing waits while a model writes`() = test { session, _ ->
        session.advance()
        session.typeTurn("sneaked in")
        assertEquals("", session.playing.turn.text)
    }

    @Test
    fun `what is unsaved is saved when the screen goes`() = runTest(UnconfinedTestDispatcher()) {
        val repository = InMemoryStories(salt)
        val scope = CoroutineScope(coroutineContext + Job())
        val session = DefaultPlaySession(scope, repository, beats, CountingIds(), drafter)
        session.start(salt.id)
        session.typeTurn("I wait.")
        scope.cancel()
        runCurrent()
        assertEquals("I wait.", repository.stored().unsent.turn)
    }

    @Test
    fun `an open note survives the screen going`() = test { session, repository ->
        session.openReason()
        session.typeNote("Too neat.")
        advanceTimeBy(DefaultPlaySession.TYPING_SETTLES)
        runCurrent()
        assertEquals(Unsent(composer = Composer.Reason("Too neat.")), repository.stored().unsent)
    }

    // Leaving

    @Test
    fun `back leaves, saving what was typed`() = test { session, repository ->
        session.typeTurn("I wait.")
        session.outcomes.test {
            session.back()
            assertEquals(PlayOutcome.Left, awaitItem())
        }
        assertEquals("I wait.", repository.stored().unsent.turn)
    }

    @Test
    fun `back while a model writes asks first, and staying lets it finish`() = test { session, _ ->
        session.advance()
        session.back()
        assertTrue(session.playing.confirmingLeave)
        session.answerLeave(LeaveAnswer.Stay)
        assertFalse(session.playing.confirmingLeave)
        beats.last.written("The key is cold.")
        assertEquals("The key is cold.", session.playing.texts.last())
    }

    @Test
    fun `leaving while a model writes stops it and keeps the story as it was`() = test { session, repository ->
        session.typeTurn("I run.")
        session.advance()
        beats.last.writing("You run")
        session.back()
        session.outcomes.test {
            session.answerLeave(LeaveAnswer.Leave)
            assertEquals(PlayOutcome.Left, awaitItem())
        }
        assertTrue(beats.last.cancelled)
        assertEquals(salt.beats, repository.stored().beats)
        assertEquals("I run.", repository.stored().unsent.turn)
    }

    @Test
    fun `the question goes away when the writing ends on its own`() = test { session, _ ->
        session.advance()
        session.back()
        beats.last.written("Done.")
        assertFalse(session.playing.confirmingLeave)
        session.answerLeave(LeaveAnswer.Leave)
        assertEquals("Done.", session.playing.texts.last())
    }

    @Test
    fun `back before the story opens leaves at once`() = test(open = null) { session, _ ->
        session.outcomes.test {
            session.start(StoryId("gone"))
            session.back()
            assertEquals(PlayOutcome.Left, awaitItem())
        }
    }

    @Test
    fun `a new beat gets a fresh id`() = test(Story(StoryId("salt"), StorySetup(), listOf(opening))) { session, _ ->
        session.typeTurn("I go.")
        session.advance()
        beats.last.written("You go.")
        val ids = session.playing.beats.map(PlayBeat::id)
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.drop(1).all { it != BeatId("b-open") })
    }
}
