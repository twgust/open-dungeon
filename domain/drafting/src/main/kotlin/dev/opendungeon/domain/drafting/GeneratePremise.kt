package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StoryDraft
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Writes a story's premise from what the player chose before it, streaming it as it is written. */
public interface GeneratePremise {
    /**
     * A cold flow: nothing is requested until it is collected, and cancelling the collector cancels the
     * request. It ends with exactly one [WritingProgress.Written] or [WritingProgress.Failed]. A premise
     * already in [draft] is one to move away from, so a reroll changes what is on screen.
     */
    public operator fun invoke(draft: StoryDraft, model: DraftingModel): Flow<WritingProgress>
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultGeneratePremise(private val generator: TextGenerator, sentences: SentenceBuilder) :
    GeneratePremise {

    private val prompt = PremisePrompt(StoryBrief(sentences))

    override fun invoke(draft: StoryDraft, model: DraftingModel): Flow<WritingProgress> = flow {
        val request = TextRequest(
            model = model.model,
            messages = prompt.messages(draft),
            tuning = model.tuning,
            sessionId = draft.id.value,
        )
        val text = StringBuilder()
        generator.stream(request).collect { event ->
            when (event) {
                is TextEvent.Delta -> {
                    text.append(event.text)
                    // Leading whitespace and an opening quote arrive first and would only flicker.
                    text.toString().cleaned().takeIf(String::isNotEmpty)?.let { emit(WritingProgress.Writing(it)) }
                }
                is TextEvent.Reasoning -> Unit
                is TextEvent.Completed -> {
                    val premise = text.toString().cleaned()
                    emit(
                        if (premise.isEmpty()) {
                            WritingProgress.Failed(DraftingError.Unusable)
                        } else {
                            WritingProgress.Written(premise)
                        },
                    )
                }
                is TextEvent.Failed -> emit(WritingProgress.Failed(DraftingError.Generation(event.error)))
            }
        }
    }

    /**
     * Models asked for bare prose still wrap it in quotes now and then. Quotes are only taken off when
     * nothing inside is quoted, so dialogue in the premise keeps its marks.
     */
    private fun String.cleaned(): String {
        val text = trim()
        val inner = text.removePrefix(QUOTE).removeSuffix(QUOTE)
        return if (QUOTE in inner) text else inner.trim()
    }

    private companion object {
        const val QUOTE = "\""
    }
}

/** The instructions and the ask for a premise. */
internal class PremisePrompt(private val brief: StoryBrief) {

    fun messages(draft: StoryDraft): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(brief.of(draft, withPremise = false))
                draft.premise?.let {
                    append("\n\nThe player passed on this premise; write a different one:\n${it.text}")
                }
                append("\n\nWrite the premise.")
            },
        ),
    )

    private companion object {
        val SYSTEM =
            """
            You write the premise of an interactive story. The player plays its main character, who is addressed as "you".
            Write two or three sentences, in the present tense, that set up the situation the story opens on: who you are, what is at stake, and what is about to happen.
            Use only what the story gives you and what follows from it. Leave the other people unnamed; they are cast later.
            Answer with the premise alone: no title, no quotation marks, no preamble.
            """.trimIndent()
    }
}
