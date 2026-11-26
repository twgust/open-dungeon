package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.core.model.ModelId
import dev.opendungeon.domain.story.CastMember
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.domain.story.Take
import dev.opendungeon.domain.story.TakeId
import dev.opendungeon.feature.newstory.domain.policy.CastPolicy
import dev.opendungeon.feature.newstory.domain.policy.FirstLinePolicy
import dev.opendungeon.feature.newstory.domain.policy.FirstTurnPolicy
import dev.opendungeon.feature.newstory.domain.policy.LimitsPolicy
import dev.opendungeon.feature.newstory.domain.policy.PremisePolicy
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

        assertEquals(ContentLimits(ContentLimits.Desire.AllTheWay, ContentLimits.Violence.Offstage, "no harm to animals"), noted.draft.limits)
        assertFalse(desire.advance || violence.advance || noted.advance)
        assertNull(limits.edit(noted.draft, DraftEdit.LimitsNote(" ")).let { it!!.limits.note })
        assertTrue(limits.canAdvance(emptyDraft))
    }

    @Test
    fun `the premise needs words, and edited words are the player's own, kept as typed`() {
        val premise = PremisePolicy()
        assertFalse(premise.canAdvance(emptyDraft))

        val written = premise.edit(emptyDraft.copy(premise = Phrase.Offered("A bell rings.")), DraftEdit.Premise("A bell rings twice. "))!!

        assertEquals(Phrase.Own("A bell rings twice. "), written.premise, "a field mid-sentence keeps its space")
        assertTrue(premise.canAdvance(written))
        assertNull(premise.edit(written, DraftEdit.Premise("  ")).let { it!!.premise }, "blank clears it")
        assertEquals(Phrase.Own("A bell."), premise.submitOwn(emptyDraft, "  A bell.  ").draft.premise, "submitted words are tidied")
    }

    @Test
    fun `cast members go in and out of the story, and one must be in`() {
        val cast = CastPolicy()
        val maren = CastMember(Character(CharacterId("maren"), name = "Maren"), CastMember.Origin.Offered, inStory = false)
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

        val body = assertIs<StepBody.Cast>(CastPolicy().body(draft))

        val card = body.people.single()
        assertEquals(ShownSecret.Sealed, card.secret)
        assertFalse("She sold them herself." in body.toString())
        assertEquals(
            listOf(ShownLine(CharacterLine.Wants, "Her mother's bones back."), ShownLine(CharacterLine.VoiceLine, "Count it again.")),
            card.lines,
        )
        assertTrue(card.inStory)
    }

    @Test
    fun `the first line is kept as typed, up to the limit`() {
        val firstLine = FirstLinePolicy()

        val typed = firstLine.edit(emptyDraft, DraftEdit.FirstLine("I come in.\n"))!!
        val long = firstLine.edit(emptyDraft, DraftEdit.FirstLine("x".repeat(StoryDraft.MAX_FIRST_LINE_LENGTH + 10)))!!

        assertEquals("I come in.\n", typed.firstLine)
        assertEquals(StoryDraft.MAX_FIRST_LINE_LENGTH, long.firstLine.length)
        assertTrue(firstLine.canAdvance(emptyDraft), "the first line is optional")
    }

    @Test
    fun `only a finished take can be chosen`() {
        val firstTurn = FirstTurnPolicy()
        val written = Take(TakeId("a"), ModelId("m/one"), "Rain.", Take.State.Written)
        val cut = Take(TakeId("b"), ModelId("m/two"), "The", Take.State.Interrupted)
        val draft = emptyDraft.copy(takes = listOf(written, cut))

        assertNull(firstTurn.select(draft, Choice.Take(TakeId("b"))))
        val chosen = firstTurn.select(draft, Choice.Take(TakeId("a")))!!.draft

        assertEquals(TakeId("a"), chosen.chosenTake)
        assertTrue(firstTurn.canAdvance(chosen))
        assertFalse(firstTurn.canAdvance(draft))
    }

    @Test
    fun `auditions say which take is chosen and which can be`() {
        val firstTurn = FirstTurnPolicy()
        val takes = listOf(
            Take(TakeId("a"), ModelId("m/one"), "Rain.", Take.State.Written),
            Take(TakeId("b"), ModelId("m/two"), "Mid", Take.State.Writing),
            Take(TakeId("c"), ModelId("m/three"), "The", Take.State.Interrupted),
        )

        val body = assertIs<StepBody.Auditions>(firstTurn.body(emptyDraft.copy(takes = takes, chosenTake = TakeId("a"))))

        assertEquals(listOf(true, false, false), body.auditions.map { it.chosen })
        assertEquals(listOf(true, false, false), body.auditions.map { it.canBeChosen })
        assertEquals(takes.map { it.state }, body.auditions.map { it.state })
    }
}
