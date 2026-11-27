package dev.opendungeon.tools.continuity.context

import dev.opendungeon.tools.continuity.scenario.CastId
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.scenario.ScriptedTurn

/**
 * Decides what of a story a model reads. This is the thing under test: the harness replays the same story
 * through each policy and compares how well the model keeps it straight.
 *
 * A policy sees the story the way the app's context engine will, one turn at a time, so an engine that
 * extracts, summarises or indexes as turns arrive fits the same shape as a plain recent-turns window.
 */
internal interface ContextPolicy {
    /** How the policy appears in reports, e.g. `recent:3` or `perceived/full`. */
    val name: String

    /** A fresh session for [scenario]; sessions never share state. */
    fun open(scenario: Scenario): ContextSession
}

/** One replay of a story through a policy. Not safe for concurrent use: the runner drives it turn by turn. */
internal interface ContextSession {
    /** [turn] has just been played. Turns arrive once each, in story order. */
    suspend fun advance(turn: ScriptedTurn)

    /** What [reader] would be given now, as the parts of a prompt that carry story. */
    suspend fun contextFor(reader: Reader): List<ContextBlock>
}

/** Who a prompt is built for. Only a character reader can be kept from what they never perceived. */
internal sealed interface Reader {
    /** The narrator, or anyone answering for the story as a whole. Knows everything the player has read. */
    data object Narrator : Reader

    /** A character speaking for themselves. */
    data class Character(val id: CastId) : Reader
}

/** A titled part of a prompt, such as the recent transcript, a summary or a list of retrieved memories. */
internal data class ContextBlock(val heading: String, val body: String)
