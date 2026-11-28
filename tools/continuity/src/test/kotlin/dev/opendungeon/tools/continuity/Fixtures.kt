package dev.opendungeon.tools.continuity

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.generation.Usage
import dev.opendungeon.domain.openrouter.testing.aiModel
import dev.opendungeon.tools.continuity.judge.FactVerdict
import dev.opendungeon.tools.continuity.judge.Judge
import dev.opendungeon.tools.continuity.judge.JudgeResult
import dev.opendungeon.tools.continuity.judge.Verdict
import dev.opendungeon.tools.continuity.judge.WhoIsWho
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.scenario.Ask
import dev.opendungeon.tools.continuity.scenario.CastId
import dev.opendungeon.tools.continuity.scenario.CastMember
import dev.opendungeon.tools.continuity.scenario.Fact
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.opendungeon.tools.continuity.scenario.Passage
import dev.opendungeon.tools.continuity.scenario.Probe
import dev.opendungeon.tools.continuity.scenario.ProbeId
import dev.opendungeon.tools.continuity.scenario.Scenario
import dev.opendungeon.tools.continuity.scenario.ScenarioCodec
import dev.opendungeon.tools.continuity.scenario.ScriptedTurn
import dev.opendungeon.tools.continuity.scenario.TurnId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal val KAEL = CastId("kael")
internal val MARA = CastId("mara")
internal val DARK = FactId("dark")
internal val POISON = FactId("poison")
internal val KEY_WITH_YOU = FactId("key-with-you")
internal val KEY_WITH_MARA = FactId("key-with-mara")

/**
 * Four turns: a public opening Kael sees, a poisoning only the protagonist sees with Kael and Mara present,
 * a key taken alone, and the key handed to Mara.
 */
internal fun tinyScenario(probes: List<Probe> = tinyProbes()): Scenario = Scenario(
    id = "tiny",
    title = "Tiny",
    premise = "A courier in a port town.",
    protagonist = CastMember(CastId("you"), "Wren", "A courier."),
    cast = listOf(CastMember(KAEL, "Kael", "A guide with a limp."), CastMember(MARA, "Mara", "An apothecary.")),
    turns = listOf(
        ScriptedTurn(TurnId("t0"), narration = listOf(Passage("The lighthouse is dark.")), present = setOf(KAEL)),
        ScriptedTurn(
            TurnId("t1"),
            player = "I watch Mara.",
            narration = listOf(
                Passage("Mara pours the wine."),
                Passage("You alone see the phial.", perceivedBy = emptySet()),
            ),
            present = setOf(KAEL, MARA),
        ),
        ScriptedTurn(
            TurnId("t2"),
            player = "I search the office.",
            narration = listOf(Passage("You pocket a brass key.")),
        ),
        ScriptedTurn(
            TurnId("t3"),
            player = "I give Mara the key.",
            narration = listOf(Passage("Mara takes the brass key.")),
            present = setOf(MARA),
        ),
    ),
    facts = listOf(
        Fact(DARK, "The lighthouse is dark.", TurnId("t0")),
        Fact(POISON, "Mara poisoned the wine.", TurnId("t1")),
        Fact(KEY_WITH_YOU, "You have the brass key.", TurnId("t2"), supersededBy = KEY_WITH_MARA),
        Fact(KEY_WITH_MARA, "Mara has the brass key.", TurnId("t3")),
    ),
    probes = probes,
)

internal fun tinyProbes(): List<Probe> = listOf(
    Probe(
        ProbeId("kael-dark"),
        after = TurnId("t1"),
        ask = Ask.Voice(KAEL, "Is the lighthouse lit?"),
        expect = setOf(DARK),
    ),
    Probe(
        ProbeId("who-key"),
        after = TurnId("t3"),
        ask = Ask.Question("Who has the key?"),
        expect = setOf(KEY_WITH_MARA),
    ),
    Probe(
        ProbeId("kael-poison"),
        after = TurnId("t3"),
        ask = Ask.Voice(KAEL, "Who poisoned the wine?"),
        forbid = setOf(POISON),
        guard = setOf(DARK),
    ),
)

internal fun saltmere(): Scenario =
    ScenarioCodec.decode(requireNotNull(Scenario::class.java.getResource("/scenarios/saltmere.json")).readText())

internal val SUBJECT = ModelChoice(aiModel("deepseek/deepseek-v4.1-flash"), Tuning())
internal val JUDGE = ModelChoice(aiModel("z-ai/glm-5.3-flash"), Tuning(temperature = 0.0))

/** Answers every request through [answer], and remembers each request and the most requests in flight at once. */
internal class FakeTextGenerator(
    private val answer: (TextRequest) -> List<TextEvent> = { listOf(TextEvent.Delta("An answer."), completed()) },
) : TextGenerator {
    val requests: MutableList<TextRequest> = CopyOnWriteArrayList()
    private val inFlight = AtomicInteger()
    val mostInFlight = AtomicInteger()

    override fun stream(request: TextRequest): Flow<TextEvent> = flow {
        requests += request
        mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
        try {
            delay(10)
            answer(request).forEach { emit(it) }
        } finally {
            inFlight.decrementAndGet()
        }
    }
}

internal fun completed(promptTokens: Int = 100, cost: String? = "0.0001"): TextEvent.Completed =
    TextEvent.Completed(Usage(promptTokens, completionTokens = 20, cost = cost?.let(::BigDecimal)), cutOff = false)

/** Judges through [verdict], one fact at a time; remembers every passage it read. */
internal class FakeJudge(private val verdict: (passage: String, fact: Fact) -> Verdict = { _, _ -> Verdict.Absent }) :
    Judge {
    val passages: MutableList<String> = CopyOnWriteArrayList()

    override suspend fun judge(passage: String, facts: List<Fact>, people: WhoIsWho, model: ModelChoice): JudgeResult {
        passages += passage
        return JudgeResult.Judged(facts.associate { it.id to FactVerdict(verdict(passage, it), quote = "…") })
    }
}

/** Hands out [answers] in order, whatever is asked, and remembers each request. */
internal class FakeStructuredGenerator(vararg answers: Outcome<Any, GenerationError>) : StructuredGenerator {
    private val queue = ArrayDeque(answers.toList())
    val requests: MutableList<StructuredRequest<*>> = CopyOnWriteArrayList()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> generate(request: StructuredRequest<T>): Outcome<T, GenerationError> {
        requests += request
        return synchronized(queue) { queue.removeFirst() } as Outcome<T, GenerationError>
    }
}
