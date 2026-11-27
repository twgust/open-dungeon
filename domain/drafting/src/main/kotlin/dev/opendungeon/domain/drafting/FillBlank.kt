package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Writes more words for one blank of a seed, once the player has seen every word it came with. */
public interface FillBlank {
    /**
     * [seed] with new unused words in its blank [key], none of them one it already has, or
     * [DraftingError.Unusable] when the model wrote nothing new that fits.
     */
    public suspend operator fun invoke(seed: Seed, key: String, model: DraftingModel): Outcome<Seed, DraftingError>
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultFillBlank(private val generator: StructuredGenerator) : FillBlank {

    override suspend fun invoke(seed: Seed, key: String, model: DraftingModel): Outcome<Seed, DraftingError> {
        val blank = requireNotNull(seed.blank(key)) { "The seed has no blank called $key" }
        val request = StructuredRequest(
            model = model.model,
            messages = FillsPrompt.messages(seed, blank),
            serializer = FillsPayload.serializer(),
            descriptions = FillsPayload.DESCRIPTIONS,
            examples = listOf(FillsPayload.EXAMPLE),
            tuning = model.tuning,
        )
        val fills = when (val answer = generator.generate(request)) {
            is Outcome.Failure -> return Outcome.Failure(DraftingError.Generation(answer.error))
            is Outcome.Success -> answer.value.fills
        }
        val more = blank.withMore(fills)
        return if (more.unused.size == blank.unused.size) {
            Outcome.Failure(DraftingError.Unusable)
        } else {
            Outcome.Success(seed.with(more))
        }
    }
}

/** The instructions and the ask for more words in one blank. */
internal object FillsPrompt {

    fun messages(seed: Seed, blank: Seed.Blank): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append("Genre: ${seed.genre}, told ${seed.tone}.")
                append("\nSentence: ${seed.template}")
                seed.blanks.filter { it.key != blank.key }.forEach { append("\n{${it.key}} is now: ${it.fill}") }
                append("\n\nFill {${blank.key}}, which asks for ${blank.kind.word}.")
                append("\nThe player has seen these, so write different ones:")
                blank.shown.forEach { append("\n- $it") }
            },
        ),
    )

    private val SYSTEM =
        """
        You fill one blank in a one-line story premise. The player plays the main character, addressed as "you".
        Write six fills that read well in the sentence, fit its genre, and differ from each other and from what the player has seen. Surprise the player.
        Keep each fill under 40 characters. Put any article the sentence needs inside the fill.
        """.trimIndent()
}

@Serializable
@SerialName("Fills")
internal data class FillsPayload(val fills: List<String>) {
    companion object {
        val DESCRIPTIONS: Map<String, String> = mapOf(
            "Fills.fills" to "Six fills for the blank, best first, each as it reads in the sentence",
        )

        val EXAMPLE = FillsPayload(
            listOf(
                "a live goose",
                "forty pounds of glitter",
                "an exorcist",
                "a decommissioned tank",
                "a llama",
                "a ghost",
            ),
        )
    }
}
