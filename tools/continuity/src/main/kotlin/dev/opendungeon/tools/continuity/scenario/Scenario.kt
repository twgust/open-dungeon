package dev.opendungeon.tools.continuity.scenario

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A scripted story with its ground truth: every turn as written, the facts the turns establish, and the
 * probes that ask about those facts at chosen points.
 *
 * The story is told to the player in the second person, so the protagonist perceives every turn. Who
 * else perceived a passage is recorded per passage, which is what lets a probe ask a character about
 * something they never saw.
 */
@Serializable
internal data class Scenario(
    val id: String,
    val title: String,
    val premise: String,
    val protagonist: CastMember,
    /** Everyone but the protagonist. */
    val cast: List<CastMember>,
    /** In story order. The first is the opening and has no player line. */
    val turns: List<ScriptedTurn>,
    val facts: List<Fact>,
    val probes: List<Probe>,
) {
    fun member(id: CastId): CastMember? = cast.firstOrNull { it.id == id }

    fun fact(id: FactId): Fact? = facts.firstOrNull { it.id == id }
}

@JvmInline
@Serializable
internal value class CastId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
internal value class TurnId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
internal value class FactId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
internal value class ProbeId(val value: String) {
    override fun toString(): String = value
}

/** Someone in the story, with the public description every reader may see. Secrets never go here. */
@Serializable
internal data class CastMember(val id: CastId, val name: String, val sheet: String)

/**
 * One turn: what the player wrote, then the narration that answered it.
 *
 * @property present the people in the scene besides the protagonist. They hear the player's line and
 *   perceive every passage that does not say otherwise.
 * @property interlude written by the pad command between two hand-written turns; never establishes a fact.
 */
@Serializable
internal data class ScriptedTurn(
    val id: TurnId,
    val player: String? = null,
    val narration: List<Passage>,
    val present: Set<CastId> = emptySet(),
    val interlude: Boolean = false,
) {
    /** Whether [reader] perceived anything of this turn: the player's line or any passage. */
    fun perceivedBy(reader: CastId): Boolean =
        (player != null && reader in present) || narration.any { reader in it.perceivers(present) }

    /** The narration as one text, the way the player reads it. */
    val narrationText: String get() = narration.joinToString(separator = " ") { it.text }
}

/**
 * Part of a turn's narration.
 *
 * @property perceivedBy who besides the protagonist perceived it; `null` means everyone present. A
 *   whisper, a hidden action or a letter read alone names its few perceivers, often none.
 */
@Serializable
internal data class Passage(val text: String, val perceivedBy: Set<CastId>? = null) {
    fun perceivers(present: Set<CastId>): Set<CastId> = perceivedBy ?: present
}

/**
 * Something a turn makes true, written as one plain claim the judge can check an answer against.
 *
 * @property supersededBy a later fact that replaces this one, such as the key changing hands. A probe
 *   after that point expects the new fact, and an answer stating the old one contradicts it.
 */
@Serializable
internal data class Fact(val id: FactId, val text: String, val establishedIn: TurnId, val supersededBy: FactId? = null)

/**
 * A question put to the model right after turn [after], and the facts its answer is checked against.
 *
 * @property expect facts the answer must state. Missing one is a recall failure.
 * @property forbid facts the answer must not state. Stating one is a knowledge leak.
 * @property guard facts the answer need not mention but must not contradict.
 */
@Serializable
internal data class Probe(
    val id: ProbeId,
    val after: TurnId,
    val ask: Ask,
    val expect: Set<FactId> = emptySet(),
    val forbid: Set<FactId> = emptySet(),
    val guard: Set<FactId> = emptySet(),
    /** Why the probe exists, for whoever reads the report. */
    val note: String? = null,
) {
    val facts: Set<FactId> get() = expect + forbid + guard
}

/** How a probe asks. Each form reads the story through a different reader and tests a different failure. */
@Serializable
internal sealed interface Ask {
    /** A reader's question about the story. Tests whether the context still holds the fact at all. */
    @Serializable
    @SerialName("question")
    data class Question(val text: String) : Ask

    /** The player's next turn, which the narrator answers. Tests whether the story continues without contradiction. */
    @Serializable
    @SerialName("action")
    data class Action(val text: String) : Ask

    /** The protagonist says [line] to [speaker], who answers. Tests what that character knows, and what they should not. */
    @Serializable
    @SerialName("voice")
    data class Voice(val speaker: CastId, val line: String) : Ask
}
