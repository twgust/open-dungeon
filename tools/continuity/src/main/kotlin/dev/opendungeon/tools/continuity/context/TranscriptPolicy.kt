package dev.opendungeon.tools.continuity.context

import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.scenario.ScriptedTurn

/**
 * The baselines: a window over the verbatim transcript, optionally cut down to what the reader perceived.
 * They are what the context engine has to beat, and the perceived projection is the leak-free reference
 * the knowledge ledger should reach.
 */
internal class TranscriptPolicy(private val window: Window, private val projection: Projection) : ContextPolicy {

    override val name: String = when (projection) {
        Projection.Omniscient -> window.spec
        Projection.Perceived -> "${Projection.PERCEIVED_PREFIX}${window.spec}"
    }

    override fun open(scenario: Scenario): ContextSession = Session(scenario)

    private inner class Session(private val scenario: Scenario) : ContextSession {
        private val played = mutableListOf<ScriptedTurn>()

        override suspend fun advance(turn: ScriptedTurn) {
            played += turn
        }

        override suspend fun contextFor(reader: Reader): List<ContextBlock> {
            val visible = project(reader)
            val shown = window.select(visible)
            val total = visible.sumOf { it.size }
            val heading = buildString {
                append(
                    when (reader) {
                        is Reader.Character if projection == Projection.Perceived ->
                            "What ${scenario.member(reader.id)?.name ?: reader.id} saw and heard"
                        else -> "The story so far"
                    },
                )
                if (shown.size < total) append(", its earlier part left out")
            }
            val body = when {
                shown.isNotEmpty() -> shown.joinToString(separator = "\n\n") { it.render() }
                total == 0 -> NOTHING_YET
                else -> LEFT_OUT
            }
            return listOf(ContextBlock(heading, body))
        }

        /** The turns [reader] may read, oldest first, each as its lines; a turn they perceived nothing of is left out. */
        private fun project(reader: Reader): List<List<TranscriptEntry>> = played.map { turn ->
            when {
                reader is Reader.Character && projection == Projection.Perceived -> turn.entriesPerceivedBy(reader)
                else -> turn.entries()
            }
        }.filter { it.isNotEmpty() }
    }

    private fun ScriptedTurn.entries(): List<TranscriptEntry> = buildList {
        player?.let { add(TranscriptEntry.PlayerLine(it)) }
        add(TranscriptEntry.Narration(narrationText))
    }

    private fun ScriptedTurn.entriesPerceivedBy(reader: Reader.Character): List<TranscriptEntry> = buildList {
        if (player != null && reader.id in present) add(TranscriptEntry.PlayerLine(player))
        val seen = narration.filter { reader.id in it.perceivers(present) }
        if (seen.isNotEmpty()) add(TranscriptEntry.Narration(seen.joinToString(separator = " ") { it.text }))
    }

    /** Which part of the visible transcript goes in. */
    sealed interface Window {
        val spec: String

        /** The lines that go in, oldest first, from [turns], each turn's lines oldest first. */
        fun select(turns: List<List<TranscriptEntry>>): List<TranscriptEntry>

        /**
         * Nothing of the story, only its setup. The floor: what a reader answers from the premise and the
         * cast's public sheets alone. A leak this reader commits too is a guess the setup invites, not a
         * fact that got through.
         */
        data object None : Window {
            override val spec: String = "none"

            override fun select(turns: List<List<TranscriptEntry>>): List<TranscriptEntry> = emptyList()
        }

        /** Everything: the upper bound on recall, and the bill no long story can pay. */
        data object All : Window {
            override val spec: String = "full"

            override fun select(turns: List<List<TranscriptEntry>>): List<TranscriptEntry> = turns.flatten()
        }

        /** The last [turns] turns verbatim, a turn being a player line and the narration that answered it. */
        data class LastTurns(val turns: Int) : Window {
            init {
                require(turns >= 1) { "Keep at least one turn, not $turns" }
            }

            override val spec: String = "recent:$turns"

            override fun select(turns: List<List<TranscriptEntry>>): List<TranscriptEntry> =
                turns.takeLast(this.turns).flatten()
        }

        /**
         * The newest entries up to [budget] characters, the newest always included however long. This is
         * how the app chooses today (`RecentBeats` in `:domain:drafting`, 12,000 characters), until the
         * context engine replaces it.
         */
        data class Characters(val budget: Int) : Window {
            init {
                require(budget >= 1) { "A budget is at least one character, not $budget" }
            }

            override val spec: String = "chars:$budget"

            override fun select(turns: List<List<TranscriptEntry>>): List<TranscriptEntry> {
                val entries = turns.flatten()
                var left = budget
                val kept = ArrayDeque<TranscriptEntry>()
                for (entry in entries.asReversed()) {
                    if (kept.isNotEmpty() && entry.text.length > left) break
                    kept.addFirst(entry)
                    left -= entry.text.length
                }
                return kept.toList()
            }
        }
    }

    enum class Projection {
        /** The reader gets the whole transcript, whoever they are. What every prompt gets today. */
        Omniscient,

        /** A character reader gets only what they perceived; the narrator still gets everything. */
        Perceived,

        ;

        companion object {
            const val PERCEIVED_PREFIX: String = "perceived/"
        }
    }

    private companion object {
        const val NOTHING_YET = "Nothing yet."
        const val LEFT_OUT = "Left out."
    }
}

/** One line of a transcript, marked the way the app marks beats for the narrator. */
internal sealed interface TranscriptEntry {
    val text: String

    fun render(): String

    data class PlayerLine(override val text: String) : TranscriptEntry {
        override fun render(): String = "[The player's turn]\n$text"
    }

    data class Narration(override val text: String) : TranscriptEntry {
        override fun render(): String = "[Story]\n$text"
    }
}
