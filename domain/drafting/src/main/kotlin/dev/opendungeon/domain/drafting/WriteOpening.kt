package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterLine
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StoryDraft
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Writes how a story begins from the genre, premise and cast, or goes on from what the player already
 * wrote, streaming as it writes.
 */
public interface WriteOpening {
    /**
     * A cold flow: nothing is requested until it is collected, and cancelling the collector cancels the
     * request. It ends with exactly one [WritingProgress.Written] or [WritingProgress.Failed].
     *
     * With a blank [StoryDraft.opening] it writes the whole opening; otherwise it continues after it.
     * Every text it reports is what goes after the draft's opening, led by whatever space separates the
     * two, so appending it never changes a character of what is already there.
     */
    public operator fun invoke(draft: StoryDraft, model: DraftingModel): Flow<WritingProgress>
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultWriteOpening(private val generator: TextGenerator, sentences: SentenceBuilder) : WriteOpening {

    private val prompt = OpeningPrompt(StoryBrief(sentences))

    override fun invoke(draft: StoryDraft, model: DraftingModel): Flow<WritingProgress> = flow {
        val request = TextRequest(
            model = model.model,
            messages = prompt.messages(draft),
            tuning = model.tuning,
            sessionId = draft.id.value,
        )
        val written = StringBuilder()
        var shown = ""
        generator.stream(request).collect { event ->
            when (event) {
                is TextEvent.Delta -> {
                    written.append(event.text)
                    val addition = Continuation.of(draft.opening, written.toString())
                    // Whitespace arrives on its own now and then; only text worth showing is reported.
                    if (addition.isNotBlank() && addition != shown) {
                        shown = addition
                        emit(WritingProgress.Writing(addition))
                    }
                }
                is TextEvent.Reasoning -> Unit
                is TextEvent.Completed -> {
                    val addition = Continuation.of(draft.opening, written.toString())
                    emit(
                        if (addition.isBlank()) {
                            WritingProgress.Failed(DraftingError.Unusable)
                        } else {
                            WritingProgress.Written(addition)
                        },
                    )
                }
                is TextEvent.Failed -> emit(WritingProgress.Failed(DraftingError.Generation(event.error)))
            }
        }
    }
}

/** How a model's words go after the player's, so the two read as one text. */
internal object Continuation {

    /**
     * [written] trimmed, led by the space that joins it to [existing]: none when [existing] is blank or
     * already ends in whitespace, a paragraph break when the model started a new paragraph, otherwise a
     * single space. Blank when nothing has been written yet.
     */
    fun of(existing: String, written: String): String {
        val text = written.trim()
        if (text.isEmpty()) return ""
        val separator = when {
            existing.isBlank() || existing.last().isWhitespace() -> ""
            written.takeWhile(Char::isWhitespace).contains('\n') -> PARAGRAPH
            else -> SPACE
        }
        return separator + text
    }

    private const val PARAGRAPH = "\n\n"
    private const val SPACE = " "
}

/** The instructions and the ask for an opening, written fresh or continued from the player's text. */
internal class OpeningPrompt(private val brief: StoryBrief) {

    fun messages(draft: StoryDraft): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(brief.of(draft, withPremise = true))
                val people = draft.castInStory
                if (people.isNotEmpty()) {
                    append("\n\nPeople in the story:")
                    people.forEach { append("\n- ${it.sheet()}") }
                }
                if (draft.opening.isBlank()) {
                    append("\n\n$FRESH")
                } else {
                    append("\n\nThe opening so far:\n${draft.opening}\n\n$CONTINUE")
                }
            },
        ),
    )

    /**
     * Everything the player can see of a person. Their secret stays out: the player reads the opening,
     * and a secret in the prompt is one the prose can give away.
     */
    private fun Character.sheet(): String = buildString {
        append(tag() ?: SOMEONE)
        SHEET_LINES.forEach { line -> this@sheet[line]?.let { append("\n  - ${line.label}: $it") } }
    }

    private companion object {
        val SYSTEM =
            """
            You write the opening of an interactive story. The player plays its main character, who is addressed as "you", in the second person and the present tense.
            Use only what the story gives you and what follows from it. Bring in the people it names by name.
            Never decide what the player does, says, thinks or feels. Stop at a moment the player can answer.
            Answer with the story text alone: plain prose, no title, no headings, no preamble, no list of choices.
            """.trimIndent()

        const val FRESH = "Write how the story begins, in two or three short paragraphs."

        const val CONTINUE =
            "Continue the opening from exactly where it stops, in the same voice, in one or two short " +
                "paragraphs. Do not repeat or rewrite any of it. If it stops mid-sentence, finish that sentence first."

        const val SOMEONE = "Someone"

        /** A person's lines after their name and role, which [tag] already gives. */
        val SHEET_LINES: List<CharacterLine> = CharacterLine.entries - CharacterLine.Name - CharacterLine.Role
    }
}
