package dev.opendungeon.feature.newstory.domain

import app.cash.turbine.test
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.policy.CastPolicy
import dev.opendungeon.feature.newstory.domain.policy.LimitsPolicy
import dev.opendungeon.feature.newstory.domain.policy.OpeningPolicy
import dev.opendungeon.feature.newstory.domain.policy.PremisePolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OtherPoliciesTest {

    @Test
    fun `limits dials and note change the limits and never leave the step`() {
        val limits = LimitsPolicy()

        val desire = limits.select(emptyDraft, Choice.Desire(ContentLimits.Desire.AllTheWay))!!
        val violence = limits.select(desire.draft, Choice.Violence(ContentLimits.Violence.Offstage))!!
        val noted = limits.submitOwn(violence.draft, "  no harm to animals ")

        assertEquals(
            ContentLimits(ContentLimits.Desire.AllTheWay, ContentLimits.Violence.Offstage, "no harm to animals"),
            noted.draft.limits,
        )
        assertFalse(desire.advance || violence.advance || noted.advance)
        assertNull(limits.edit(noted.draft, DraftEdit.LimitsNote(" ")).let { it!!.limits.note })
        assertTrue(limits.canAdvance(emptyDraft))
    }

    @Test
    fun `the premise needs words, and edited words are the player's own, kept as typed`() {
        val premise = PremisePolicy(ScriptedWriting())
        assertFalse(premise.canAdvance(emptyDraft))

        val written = premise.edit(
            emptyDraft.copy(premise = Phrase.Offered("A bell rings.")),
            DraftEdit.Premise("A bell rings twice. "),
        )!!

        assertEquals(Phrase.Own("A bell rings twice. "), written.premise, "a field mid-sentence keeps its space")
        assertTrue(premise.canAdvance(written))
        assertNull(premise.edit(written, DraftEdit.Premise("  ")).let { it!!.premise }, "blank clears it")
        assertEquals(
            Phrase.Own("A bell."),
            premise.submitOwn(emptyDraft, "  A bell.  ").draft.premise,
            "submitted words are tidied",
        )
    }

    @Test
    fun `cast members go in and out of the story, and one must be in`() {
        val cast = CastPolicy(ScriptedCast())
        val maren =
            CastMember(Character(CharacterId("maren"), name = "Maren"), CastMember.Origin.Offered, inStory = false)
        val draft = emptyDraft.copy(cast = listOf(maren))
        assertFalse(cast.canAdvance(draft))

        val added = cast.select(draft, Choice.CastMember(CharacterId("maren")))!!

        assertTrue(added.draft.cast.single().inStory)
        assertTrue(cast.canAdvance(added.draft))
        assertNull(cast.select(draft, Choice.CastMember(CharacterId("nobody"))))
    }

    @Test
    fun `a cast card shows a sealed secret only as a flag, and only the lines that are filled`() {
        val maren = Character(
            id = CharacterId("maren"),
            role = "your employer",
            name = "Maren Kask",
            wants = "Her mother's bones back.",
            flaw = "Trusts no one twice.",
            voiceLine = "Count it again.",
            secret = Secret.Sealed("She sold them herself."),
        )
        val draft = emptyDraft.copy(cast = listOf(CastMember(maren, CastMember.Origin.Offered, inStory = true)))

        val body = assertIs<StepBody.Cast>(CastPolicy(ScriptedCast()).body(draft))

        val card = body.people.single()
        assertEquals(ShownSecret.Sealed, card.secret)
        assertFalse("She sold them herself." in body.toString())
        assertEquals(
            listOf(
                ShownLine(CharacterLine.Wants, "Her mother's bones back."),
                ShownLine(CharacterLine.VoiceLine, "Count it again."),
            ),
            card.lines,
        )
        assertTrue(card.inStory)
    }

    @Test
    fun `the opening is kept as typed, up to the limit`() {
        val opening = OpeningPolicy(ScriptedWriting())

        val typed = opening.edit(emptyDraft, DraftEdit.Opening("I come in.\n"))!!
        val long = opening.edit(emptyDraft, DraftEdit.Opening("x".repeat(StoryDraft.MAX_OPENING_LENGTH + 10)))!!

        assertEquals("I come in.\n", typed.opening)
        assertEquals(StoryDraft.MAX_OPENING_LENGTH, long.opening.length)
    }

    @Test
    fun `the story can begin once the opening has words in it`() {
        val opening = OpeningPolicy(ScriptedWriting())

        assertFalse(opening.canAdvance(emptyDraft))
        assertFalse(opening.canAdvance(emptyDraft.copy(opening = " \n")))
        assertTrue(opening.canAdvance(emptyDraft.copy(opening = "R")))
    }

    @Test
    fun `the wand writes it all when the field is empty, and carries on when it is not`() {
        val opening = OpeningPolicy(ScriptedWriting())

        fun wand(text: String) = assertIs<StepBody.Opening>(opening.body(emptyDraft.copy(opening = text))).wand

        assertEquals(StepBody.Opening.Wand.WriteForMe, wand(""))
        assertEquals(StepBody.Opening.Wand.ContinueMyText, wand("You wait."))
    }

    @Test
    fun `the wand works until the opening is full`() {
        val opening = OpeningPolicy(ScriptedWriting())

        assertTrue(opening.canRoll(emptyDraft))
        assertTrue(opening.canRoll(emptyDraft.copy(opening = "x".repeat(StoryDraft.MAX_OPENING_LENGTH - 1))))
        assertFalse(opening.canRoll(emptyDraft.copy(opening = "x".repeat(StoryDraft.MAX_OPENING_LENGTH))))
    }

    @Test
    fun `while writing, the model's words stay apart from the text it started from`() {
        val opening = OpeningPolicy(ScriptedWriting())
        val from = emptyDraft.copy(opening = "You wait.")

        val waiting = opening.body(from, WriterStatus.Writing(from, preview = null))
        val adding = opening.body(from, WriterStatus.Writing(from, preview = from.copy(opening = "You wait. Maren")))

        assertEquals(
            StepBody.Opening(
                text = "You wait.",
                writing = StepBody.Opening.Writing.Waiting,
                wand = StepBody.Opening.Wand.ContinueMyText,
                addition = null,
                failed = false,
                maxLength = StoryDraft.MAX_OPENING_LENGTH,
            ),
            waiting,
        )
        assertEquals(StepBody.Opening.Writing.Adding(" Maren"), assertIs<StepBody.Opening>(adding).writing)
        assertEquals("You wait.", adding.text)
    }

    @Test
    fun `what the last writing left shows on the step`() {
        val opening = OpeningPolicy(ScriptedWriting())
        val draft = emptyDraft.copy(opening = "You wait. Maren is late.")

        val kept = assertIs<StepBody.Opening>(opening.body(draft, WriterStatus.Kept(stopped = true)))
        val failed = assertIs<StepBody.Opening>(opening.body(draft, WriterStatus.Failed(DraftingError.Unusable)))

        assertEquals(StepBody.Opening.Addition(stopped = true), kept.addition)
        assertFalse(kept.failed)
        assertTrue(failed.failed)
        assertNull(failed.addition)
    }

    @Test
    fun `the model only ever adds after the player's text, and only up to the limit`() = runTest {
        val writing = ScriptedWriting()
        val opening = OpeningPolicy(writing)
        val mine = "x".repeat(StoryDraft.MAX_OPENING_LENGTH - 3)
        val from = emptyDraft.copy(opening = mine)

        opening.write(from, storyteller).test {
            writing.last.writing(" Maren")
            assertEquals(WriterEvent.Preview(from.copy(opening = "$mine Ma")), awaitItem())
            writing.last.written(" Maren is late.")
            assertEquals(WriterEvent.Done(from.copy(opening = "$mine Ma")), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `the opening's writer keeps what it stopped at, can be taken back, and fails in place`() {
        val opening = OpeningPolicy(ScriptedWriting())

        assertFalse(opening.writesOnArrival(emptyDraft), "writing waits for the wand")
        assertTrue(opening.keepsWhenStopped)
        assertTrue(opening.undoable)
        assertTrue(opening.failsInPlace)
    }
}
