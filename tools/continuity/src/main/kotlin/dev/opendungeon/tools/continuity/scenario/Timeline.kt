package dev.opendungeon.tools.continuity.scenario

/** Positions in a scenario's story: where each turn falls, and how far apart two points are in turns. */
internal class Timeline(private val scenario: Scenario) {

    private val positions: Map<TurnId, Int> = scenario.turns.withIndex().associate { (index, turn) -> turn.id to index }

    /** @throws IllegalArgumentException when [turn] is not in the scenario; validate it first. */
    fun positionOf(turn: TurnId): Int = requireNotNull(positions[turn]) { "No turn $turn in ${scenario.id}" }

    /** Turns between the one that established [fact] and the one [probe] follows: 0 when it is the same turn. */
    fun distance(fact: Fact, probe: Probe): Int = positionOf(probe.after) - positionOf(fact.establishedIn)

    /** Whether [fact] is part of the story by the end of [turn]. */
    fun isEstablished(fact: Fact, turn: TurnId): Boolean = positionOf(fact.establishedIn) <= positionOf(turn)

    /** Whether something established by the end of [turn] has replaced [fact]. */
    fun isSuperseded(fact: Fact, turn: TurnId): Boolean {
        val successor = fact.supersededBy?.let(scenario::fact) ?: return false
        return isEstablished(successor, turn)
    }

    /** The facts that hold at the end of [turn]: established and not replaced. */
    fun currentFacts(turn: TurnId): List<Fact> =
        scenario.facts.filter { isEstablished(it, turn) && !isSuperseded(it, turn) }
}
