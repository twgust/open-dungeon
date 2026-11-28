package dev.opendungeon.tools.continuity.probe

import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.tools.continuity.context.ContextBlock
import dev.opendungeon.tools.continuity.context.Reader
import dev.opendungeon.tools.continuity.scenario.Ask
import dev.opendungeon.tools.continuity.scenario.CastMember
import dev.opendungeon.tools.continuity.scenario.Probe
import dev.opendungeon.tools.continuity.scenario.Scenario

/**
 * Frames a probe for the model under test: the instructions for its kind of ask, the story's fixed setup,
 * the context the policy chose, and the ask itself. Only the context differs between policies, so any
 * difference in the answers is the policy's doing.
 */
internal object ProbePrompts {

    /** Who reads the story to answer [probe]: the speaker for a voice, the narrator otherwise. */
    fun readerOf(probe: Probe): Reader = when (val ask = probe.ask) {
        is Ask.Voice -> Reader.Character(ask.speaker)
        is Ask.Question, is Ask.Action -> Reader.Narrator
    }

    /**
     * The ask as a reader of the answer needs it, with none of the story: the question, the player's turn,
     * or the line said to a character. The judge reads an answer against it.
     */
    fun ask(scenario: Scenario, probe: Probe): String = when (val ask = probe.ask) {
        is Ask.Question -> "The question: ${ask.text}"
        is Ask.Action -> "The player's turn, which the passage narrates the answer to: ${ask.text}"
        is Ask.Voice -> "You say to ${speakerOf(scenario, ask).name}: \"${ask.line}\" The passage is the answer."
    }

    fun messages(scenario: Scenario, probe: Probe, context: List<ContextBlock>): List<ChatMessage> {
        val story = buildString {
            append(setup(scenario))
            context.forEach { append("\n\n").append(it.heading).append(":\n").append(it.body) }
        }
        return when (val ask = probe.ask) {
            is Ask.Question -> listOf(
                ChatMessage.System(QUESTION_SYSTEM),
                ChatMessage.User("$story\n\nThe question: ${ask.text}"),
            )
            is Ask.Action -> listOf(
                ChatMessage.System(NARRATOR_SYSTEM),
                ChatMessage.User("$story\n\n[The player's turn]\n${ask.text}\n\nWrite the next beat."),
            )
            is Ask.Voice -> {
                val speaker = speakerOf(scenario, ask)
                listOf(
                    ChatMessage.System(voiceSystem(speaker)),
                    ChatMessage.User(
                        "$story\n\nYou say to ${speaker.name}: \"${ask.line}\"\n\nWrite ${speaker.name}'s answer.",
                    ),
                )
            }
        }
    }

    private fun speakerOf(scenario: Scenario, ask: Ask.Voice): CastMember =
        requireNotNull(scenario.member(ask.speaker)) { "No ${ask.speaker} in ${scenario.id}" }

    /** The parts of a story that never change in play, the same for every policy and every reader. */
    private fun setup(scenario: Scenario): String = buildString {
        append("Premise: ").append(scenario.premise)
        append("\n\nThe player's character, addressed as \"you\": ").append(scenario.protagonist.describe())
        if (scenario.cast.isNotEmpty()) {
            append("\n\nPeople in the story:")
            scenario.cast.forEach { append("\n- ").append(it.describe()) }
        }
    }

    private fun CastMember.describe(): String = "$name. $sheet"

    private val QUESTION_SYSTEM =
        """
        You answer a question about an interactive story, using only the story text you are given. The player's character is addressed as "you".
        If the text does not tell, say that it does not; never guess or fill the gap.
        Answer in one to three plain sentences.
        """.trimIndent()

    /** The app's narrator instructions (`BeatPrompt` in `:domain:drafting`), so in-story probes test the narrator players get. */
    private val NARRATOR_SYSTEM =
        """
        You narrate an interactive story. The player plays its main character, who is addressed as "you", in the second person and the present tense.
        You write one beat at a time: one or two short paragraphs that move the story on from exactly where it stands. When the story ends on the player's turn, the beat answers that turn.
        Use only what the story gives you and what follows from it. Keep the people as they are written, and bring them in by name.
        Never decide what the player does, says, thinks or feels beyond what their turns state. Stop at a moment the player can answer.
        Answer with the beat alone: plain prose, no title, no headings, no preamble, no list of choices.
        """.trimIndent()

    private fun voiceSystem(speaker: CastMember): String =
        """
        You voice ${speaker.name}, one of the people in an interactive story. The player's character is addressed as "you".
        Answer with what ${speaker.name} says aloud, and at most a short line of what they do, in one short paragraph.
        ${speaker.name} knows only what they saw, heard or were told in the story. They cannot know anything else, and they answer you openly.
        Answer with ${speaker.name}'s reply alone: no narration of anyone else, no preamble.
        """.trimIndent()
}
