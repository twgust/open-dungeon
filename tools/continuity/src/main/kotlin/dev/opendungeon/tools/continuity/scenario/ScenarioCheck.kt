package dev.opendungeon.tools.continuity.scenario

/**
 * The structural rules a scenario must follow before any model sees it. A scenario that breaks one would
 * produce numbers that measure the scenario's mistake rather than the context policy.
 */
internal object ScenarioCheck {

    /** Every rule [scenario] breaks, in reading order; empty when it is sound. */
    fun problems(scenario: Scenario): List<String> = buildList {
        addAll(idProblems(scenario))
        addAll(turnProblems(scenario))
        // Facts and probes point at turns and facts; with broken ids those checks would only repeat the problem.
        if (isEmpty()) {
            addAll(factProblems(scenario))
            addAll(probeProblems(scenario))
        }
    }

    private fun idProblems(scenario: Scenario): List<String> = buildList {
        duplicates((scenario.cast + scenario.protagonist).map { it.id.value })
            .forEach { add("Cast id '$it' is used more than once") }
        duplicates(scenario.turns.map { it.id.value }).forEach { add("Turn id '$it' is used more than once") }
        duplicates(scenario.facts.map { it.id.value }).forEach { add("Fact id '$it' is used more than once") }
        duplicates(scenario.probes.map { it.id.value }).forEach { add("Probe id '$it' is used more than once") }
        if (scenario.premise.isBlank()) add("The premise is empty")
        (scenario.cast + scenario.protagonist).filter { it.name.isBlank() || it.sheet.isBlank() }
            .forEach { add("Cast member '${it.id}' needs a name and a sheet") }
    }

    private fun turnProblems(scenario: Scenario): List<String> = buildList {
        val cast = scenario.cast.mapTo(HashSet()) { it.id }
        if (scenario.turns.isEmpty()) add("A scenario needs at least one turn")
        scenario.turns.firstOrNull()?.let {
            if (it.player !=
                null
            ) {
                add("The opening turn '${it.id}' has a player line")
            }
        }
        scenario.turns.drop(1).filter { it.player.isNullOrBlank() }
            .forEach { add("Turn '${it.id}' has no player line; only the opening may lack one") }
        scenario.turns.forEach { turn ->
            if (turn.narration.isEmpty() || turn.narration.any { it.text.isBlank() }) {
                add("Turn '${turn.id}' has an empty passage")
            }
            (turn.present - cast).forEach { add("Turn '${turn.id}' lists '$it' as present, who is not in the cast") }
            turn.narration.forEach { passage ->
                (passage.perceivedBy.orEmpty() - turn.present).forEach {
                    add("A passage of turn '${turn.id}' is perceived by '$it', who is not present")
                }
            }
        }
    }

    private fun factProblems(scenario: Scenario): List<String> = buildList {
        val turns = scenario.turns.mapTo(HashSet()) { it.id }
        scenario.facts.forEach { fact ->
            if (fact.text.isBlank()) add("Fact '${fact.id}' is empty")
            if (fact.establishedIn !in
                turns
            ) {
                add("Fact '${fact.id}' is established in '${fact.establishedIn}', no such turn")
            }
        }
        if (isNotEmpty()) return@buildList
        val timeline = Timeline(scenario)
        scenario.facts.forEach { fact ->
            val established = scenario.turns[timeline.positionOf(fact.establishedIn)]
            if (established.interlude) add("Fact '${fact.id}' is established in an interlude; interludes are filler")
            val successorId = fact.supersededBy ?: return@forEach
            val successor = scenario.fact(successorId)
            when {
                successor == null -> add("Fact '${fact.id}' is superseded by '$successorId', no such fact")
                timeline.positionOf(successor.establishedIn) <= timeline.positionOf(fact.establishedIn) ->
                    add("Fact '${fact.id}' is superseded by '$successorId', which is not established after it")
            }
        }
    }

    private fun probeProblems(scenario: Scenario): List<String> = buildList {
        val turns = scenario.turns.mapTo(HashSet()) { it.id }
        val facts = scenario.facts.associateBy { it.id }
        val timeline = Timeline(scenario)
        scenario.probes.forEach { probe ->
            if (probe.after !in turns) {
                add("Probe '${probe.id}' follows '${probe.after}', no such turn")
                return@forEach
            }
            if (probe.facts.isEmpty()) add("Probe '${probe.id}' checks no fact")
            val roles = listOf(probe.expect, probe.forbid, probe.guard)
            if (roles.sumOf { it.size } != probe.facts.size) add("Probe '${probe.id}' gives a fact more than one role")
            (probe.facts - facts.keys).forEach { add("Probe '${probe.id}' checks '$it', no such fact") }
            (probe.expect + probe.guard).mapNotNull(facts::get).forEach { fact ->
                if (!timeline.isEstablished(fact, probe.after)) {
                    add("Probe '${probe.id}' relies on '${fact.id}' before it is established")
                } else if (fact.id in probe.expect && timeline.isSuperseded(fact, probe.after)) {
                    add("Probe '${probe.id}' expects '${fact.id}', which is superseded by then")
                }
            }
            askProblems(scenario, probe)?.let(::add)
        }
    }

    private fun askProblems(scenario: Scenario, probe: Probe): String? = when (val ask = probe.ask) {
        is Ask.Question -> "Probe '${probe.id}' asks an empty question".takeIf { ask.text.isBlank() }
        is Ask.Action -> "Probe '${probe.id}' has an empty action".takeIf { ask.text.isBlank() }
        is Ask.Voice -> when {
            scenario.member(
                ask.speaker,
            ) == null -> "Probe '${probe.id}' speaks to '${ask.speaker}', who is not in the cast"
            ask.line.isBlank() -> "Probe '${probe.id}' has an empty line"
            else -> null
        }
    }

    private fun duplicates(ids: List<String>): Set<String> =
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
