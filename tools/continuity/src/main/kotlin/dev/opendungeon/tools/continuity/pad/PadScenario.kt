package dev.opendungeon.tools.continuity.pad

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.opendungeon.tools.continuity.judge.Judge
import dev.opendungeon.tools.continuity.judge.JudgeFailure
import dev.opendungeon.tools.continuity.judge.JudgeResult
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.judge.WhoIsWho
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.scenario.Passage
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.scenario.ScriptedTurn
import dev.opendungeon.tools.continuity.scenario.Timeline
import dev.opendungeon.tools.continuity.scenario.TurnId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stretches a hand-written scenario to a long story by writing quiet interludes between its turns. The
 * facts and probes stay anchored to the hand-written turns, so every fact stays exact while the distance
 * between where it happened and where it is asked about grows.
 *
 * An interlude that states any fact (which would bring it closer) or contradicts one still holding (which
 * would make the story inconsistent) is rejected by the judge and written again.
 */
internal interface PadScenario {
    /** A cold flow that reports each interlude as it is accepted and ends with [PadEvent.Finished] or [PadEvent.Failed]. */
    fun pad(request: PadRequest): Flow<PadEvent>
}

internal data class PadRequest(
    val scenario: Scenario,
    val targetTurns: Int,
    val writer: ModelChoice,
    val judge: ModelChoice,
    val concurrency: Int,
) {
    init {
        require(
            scenario.turns.none {
                it.interlude
            },
        ) { "${scenario.id} is padded already; pad the hand-written scenario" }
        require(concurrency >= 1) { "Allow at least one call at a time, not $concurrency" }
    }
}

internal sealed interface PadEvent {
    data class InterludeAccepted(val after: TurnId, val written: Int, val total: Int) : PadEvent

    data class Finished(val scenario: Scenario) : PadEvent

    /** No acceptable interlude could be written after [after]; [reasons] are the attempts' rejections. */
    data class Failed(val after: TurnId, val reasons: List<String>) : PadEvent
}

@Inject
@ContributesBinding(AppScope::class)
internal class DefaultPadScenario(private val generator: StructuredGenerator, private val judge: Judge) : PadScenario {

    override fun pad(request: PadRequest): Flow<PadEvent> = channelFlow {
        val scenario = request.scenario
        val counts = PadPlan.spread(scenario.turns.size, request.targetTurns)
        val total = counts.sum()
        val written = AtomicInteger()
        val permits = Semaphore(request.concurrency)
        // Gaps are written side by side; within a gap each interlude follows the one before it. The first gap
        // that cannot be filled cancels the others, so no more money is spent on a padding that will not finish.
        val gaps = try {
            coroutineScope {
                counts.mapIndexed { gap, count ->
                    async {
                        val interludes = mutableListOf<ScriptedTurn>()
                        for (index in 1..count) {
                            when (
                                val attempt = permits.withPermit {
                                    writeAccepted(request, gap, index, count, interludes)
                                }
                            ) {
                                is Attempt.Accepted -> interludes += attempt.turn
                                is Attempt.Rejected -> throw GapRejected(
                                    PadEvent.Failed(scenario.turns[gap].id, attempt.reasons),
                                )
                            }
                            send(PadEvent.InterludeAccepted(scenario.turns[gap].id, written.incrementAndGet(), total))
                        }
                        interludes.toList()
                    }
                }.awaitAll()
            }
        } catch (rejected: GapRejected) {
            send(rejected.event)
            return@channelFlow
        }
        val turns = scenario.turns.flatMapIndexed { index, turn -> listOf(turn) + gaps.getOrNull(index).orEmpty() }
        send(
            PadEvent.Finished(
                scenario.copy(
                    id = "${scenario.id}-${request.targetTurns}",
                    title = "${scenario.title}, padded to ${request.targetTurns} turns",
                    turns = turns,
                ),
            ),
        )
    }

    private suspend fun writeAccepted(
        request: PadRequest,
        gap: Int,
        index: Int,
        count: Int,
        earlier: List<ScriptedTurn>,
    ): Attempt {
        val reasons = mutableListOf<String>()
        repeat(MAX_ATTEMPTS) {
            val turn = when (val written = write(request, gap, index, count, earlier, reasons)) {
                is Outcome.Failure -> {
                    reasons += written.error
                    return@repeat
                }
                is Outcome.Success -> written.value
            }
            val rejection = check(request, gap, turn) ?: return Attempt.Accepted(turn)
            reasons += rejection
        }
        return Attempt.Rejected(reasons)
    }

    private suspend fun write(
        request: PadRequest,
        gap: Int,
        index: Int,
        count: Int,
        earlier: List<ScriptedTurn>,
        rejections: List<String>,
    ): Outcome<ScriptedTurn, String> {
        val scenario = request.scenario
        val before = scenario.turns[gap]
        val structured = StructuredRequest(
            model = request.writer.model,
            messages = InterludePrompt.messages(scenario, gap, index, count, earlier, rejections),
            serializer = InterludePayload.serializer(),
            descriptions = InterludePayload.DESCRIPTIONS,
            examples = listOf(InterludePayload.EXAMPLE),
            tuning = request.writer.tuning,
        )
        return when (val answer = generator.generate(structured)) {
            is Outcome.Failure -> Outcome.Failure("The writer failed: ${answer.error}")
            is Outcome.Success -> {
                val payload = answer.value
                if (payload.player.isBlank() || payload.narration.isBlank()) {
                    Outcome.Failure("The writer left the player's line or the narration empty")
                } else {
                    Outcome.Success(
                        ScriptedTurn(
                            id = TurnId("${before.id}~$index"),
                            player = payload.player.trim(),
                            narration = listOf(Passage(payload.narration.trim())),
                            present = before.present,
                            interlude = true,
                        ),
                    )
                }
            }
        }
    }

    /** Why [turn] cannot stand in the story after hand-written turn [gap], or `null` when it can. */
    private suspend fun check(request: PadRequest, gap: Int, turn: ScriptedTurn): String? {
        val scenario = request.scenario
        val timeline = Timeline(scenario)
        val at = scenario.turns[gap].id
        val passage = "${turn.player}\n\n${turn.narrationText}"
        val result = judge.judge(passage, scenario.facts, WhoIsWho.of(scenario), request.judge)
        val verdicts = when (result) {
            is JudgeResult.Failed -> return when (val reason = result.reason) {
                is JudgeFailure.Generation -> "The judge failed: ${reason.error}"
                is JudgeFailure.Incomplete -> "The judge's verdicts were incomplete: ${reason.detail}"
            }
            is JudgeResult.Judged -> result.verdicts
        }
        val problems = scenario.facts.mapNotNull { fact ->
            val verdict = verdicts.getValue(fact.id)
            when {
                verdict.verdict == Verdict.Asserted -> "it mentions \"${fact.text}\" (\"${verdict.quote}\")"
                // An interlude only fills time; even a hint at a fact would let a probe pass on the padding.
                verdict.verdict == Verdict.Suspected -> "it hints at \"${fact.text}\" (\"${verdict.quote}\")"
                // A fact that no longer holds is meant to be contradicted by the story as it stands now.
                verdict.verdict == Verdict.Contradicted && !timeline.isSuperseded(fact, at) ->
                    "it contradicts \"${fact.text}\" (\"${verdict.quote}\")"
                else -> null
            }
        }
        return problems.takeIf { it.isNotEmpty() }?.joinToString(separator = "; ", prefix = "Rejected because ")
    }

    /** Carries a gap's failure out of its coroutine, cancelling the gaps still being written. */
    private class GapRejected(val event: PadEvent.Failed) : Exception("Gap after ${event.after} could not be filled")

    private sealed interface Attempt {
        data class Accepted(val turn: ScriptedTurn) : Attempt

        data class Rejected(val reasons: List<String>) : Attempt
    }

    private companion object {
        /** Each attempt is a paid writer call and a paid judge call; an interlude that fails three times needs a person. */
        const val MAX_ATTEMPTS = 3
    }
}

/** The instructions and the ask for one interlude. */
internal object InterludePrompt {

    /** Turns before the interlude that the writer reads, so it picks up where the story stands. */
    private const val RECENT_TURNS = 3

    fun messages(
        scenario: Scenario,
        gap: Int,
        index: Int,
        count: Int,
        earlier: List<ScriptedTurn>,
        rejections: List<String>,
    ): List<ChatMessage> {
        val before = scenario.turns[gap]
        val next = scenario.turns[gap + 1]
        val recent = (scenario.turns.take(gap + 1) + earlier).takeLast(RECENT_TURNS)
        val present = before.present.mapNotNull { scenario.member(it)?.name }
        val facts = Timeline(scenario).currentFacts(before.id)
        return listOf(
            ChatMessage.System(SYSTEM),
            ChatMessage.User(
                buildString {
                    append("Premise: ").append(scenario.premise)
                    append("\n\nThe player's character, addressed as \"you\": ")
                    append(scenario.protagonist.name).append(". ").append(scenario.protagonist.sheet)
                    append("\n\nWith you: ").append(present.joinToString().ifEmpty { "nobody" })
                    if (facts.isNotEmpty()) {
                        append(
                            "\n\nWhat is true in the story now. Stay consistent with it, but never mention, recall or hint at any of it:",
                        )
                        facts.forEach { append("\n- ").append(it.text) }
                    }
                    append("\n\nThe most recent turns:")
                    recent.forEach { turn ->
                        turn.player?.let { append("\n\n[The player's turn]\n").append(it) }
                        append("\n\n[Story]\n").append(turn.narrationText)
                    }
                    append("\n\nLater, the story goes on with the player's turn \"").append(next.player).append("\".")
                    append(" Leave the story where that can still happen.")
                    append("\n\nWrite interlude $index of $count before it.")
                    if (rejections.isNotEmpty()) {
                        append("\n\nEarlier attempts were turned down. Avoid this:")
                        rejections.forEach { append("\n- ").append(it) }
                    }
                },
            ),
        )
    }

    private val SYSTEM =
        """
        You write interludes for an interactive story: quiet turns between its main scenes. The player plays its main character, who is addressed as "you".
        An interlude is one turn: a short line the player types for their character, in the first person ("I check the knots on the mooring line"), and the story's answer, in the second person and the present tense, sixty to a hundred and twenty words.
        In an interlude the character does something small: travels, eats, waits, mends something, notices the weather, trades small talk. Nothing important happens.
        Never move the plot on, never bring in a new person who matters, an object that matters, a secret or a discovery, and never mention, recall or hint at anything the story has established. The people present may talk, but only about passing things.
        Write fresh detail each time; never repeat an earlier turn.
        """.trimIndent()
}

/** What the writer answers. Every field is required, so the schema stays valid for strict structured outputs. */
@Serializable
@SerialName("Interlude")
internal data class InterludePayload(val player: String, val narration: String) {
    companion object {
        val DESCRIPTIONS: Map<String, String> = mapOf(
            "Interlude.player" to "The player's line, in the first person, one short sentence",
            "Interlude.narration" to
                "The story's answer, second person, present tense, sixty to a hundred and twenty words",
        )

        val EXAMPLE = InterludePayload(
            player = "I buy a paper cone of fried sprats and eat them on the sea wall.",
            narration = listOf(
                "The sprats are too hot to hold and too good to wait for. You burn your fingers anyway.",
                "Below the wall the tide is out, and a boy with a rake works the mud for cockles, filling a",
                "bucket with the patience of someone who has nowhere else to be. Gulls patrol the railing a",
                "careful distance away. When the cone is empty you fold it small and tuck it in your pocket,",
                "and the wind off the flats smells of salt and tar.",
            ).joinToString(separator = " "),
        )
    }
}
