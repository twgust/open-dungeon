package dev.opendungeon.tools.continuity.judge

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.opendungeon.tools.continuity.model.ModelChoice
import dev.opendungeon.tools.continuity.scenario.Fact
import dev.opendungeon.tools.continuity.scenario.FactId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A model as judge, through the app's structured generation. It sees the passage and the facts and
 * nothing of the story, so it cannot credit an answer for what the answer did not say.
 */
@Inject
@ContributesBinding(AppScope::class)
internal class ModelJudge(private val generator: StructuredGenerator) : Judge {

    override suspend fun judge(
        passage: String,
        facts: List<Fact>,
        protagonist: String,
        model: ModelChoice,
    ): JudgeResult {
        require(facts.isNotEmpty()) { "Judge at least one fact" }
        val request = StructuredRequest(
            model = model.model,
            messages = messages(passage, facts, protagonist),
            serializer = JudgePayload.serializer(),
            descriptions = JudgePayload.DESCRIPTIONS,
            examples = listOf(JudgePayload.EXAMPLE),
            tuning = model.tuning,
        )
        return when (val answer = generator.generate(request)) {
            is Outcome.Failure -> JudgeResult.Failed(JudgeFailure.Generation(answer.error))
            is Outcome.Success -> answer.value.toResult(facts.map { it.id })
        }
    }

    private fun messages(passage: String, facts: List<Fact>, protagonist: String): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(
                    "In both the passage and the facts, \"you\" is the player's character, ",
                ).append(protagonist).append(".")
                append("\n\nThe passage:\n<<<\n").append(passage.trim()).append("\n>>>")
                append("\n\nThe facts:")
                facts.forEach { append("\n- [").append(it.id.value).append("] ").append(it.text) }
            },
        ),
    )

    /** Every fact asked about must come back exactly once; anything else means the verdicts cannot be trusted. */
    private fun JudgePayload.toResult(asked: List<FactId>): JudgeResult {
        val byFact = checks.groupBy { FactId(it.fact.trim().removeSurrounding("[", "]")) }
        val missing = asked.filterNot(byFact::containsKey)
        val unknown = byFact.keys - asked.toSet()
        val repeated = byFact.filterValues { it.size > 1 }.keys
        if (missing.isNotEmpty() || unknown.isNotEmpty() || repeated.isNotEmpty()) {
            return JudgeResult.Failed(
                JudgeFailure.Incomplete("missing $missing, not asked $unknown, repeated $repeated"),
            )
        }
        return JudgeResult.Judged(
            asked.associateWith { id -> byFact.getValue(id).single().let { FactVerdict(it.verdict, it.quote.trim()) } },
        )
    }

    private companion object {
        val SYSTEM =
            """
            You check a passage from an interactive story against a list of facts, one verdict per fact.
            - asserted: the passage states the fact, or makes it unmistakable. A paraphrase counts; a vague gesture towards it does not.
            - contradicted: the passage states or makes unmistakable something that cannot be true if the fact is.
            - absent: neither. A passage that says it does not know, or that is silent on the matter, leaves the fact absent.
            Judge only what the passage says, never what is likely or what the rest of the story might hold. A character claiming something counts as the passage stating it.
            For asserted and contradicted, quote the few words that decided it; for absent, leave the quote empty.
            """.trimIndent()
    }
}

/** What the judge answers. Every field is required, so the schema stays valid for strict structured outputs. */
@Serializable
@SerialName("Verdicts")
internal data class JudgePayload(val checks: List<CheckPayload>) {
    companion object {
        val DESCRIPTIONS: Map<String, String> = mapOf(
            "Verdicts.checks" to "One check per fact, in the order the facts were given",
            "Check.fact" to "The fact's id, as given in square brackets, without the brackets",
            "Check.verdict" to "asserted, contradicted or absent",
            "Check.quote" to "The words of the passage that decided it; empty when absent",
        )

        val EXAMPLE = JudgePayload(
            listOf(
                CheckPayload(
                    fact = "key-with-mara",
                    verdict = Verdict.Contradicted,
                    quote = "the brass key still in your pocket",
                ),
                CheckPayload(fact = "storm-coming", verdict = Verdict.Absent, quote = ""),
            ),
        )
    }
}

@Serializable
@SerialName("Check")
internal data class CheckPayload(val fact: String, val verdict: Verdict, val quote: String)
