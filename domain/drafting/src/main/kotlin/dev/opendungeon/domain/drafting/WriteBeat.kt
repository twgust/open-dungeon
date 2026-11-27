package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.story.Beat
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.Story
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** What the player asked a model to write in play. */
public sealed interface BeatAsk {
    /** The player's direction for the beat, if they gave one. Never part of the story. */
    public val guide: String?

    /** The beat after the story's last one. */
    public data class Next(override val guide: String? = null) : BeatAsk

    /**
     * The story's last beat again, which must be one a model wrote. [reason] is what the player found
     * wrong with it, if they said.
     */
    public data class Again(override val guide: String? = null, val reason: String? = null) : BeatAsk
}

/** Writes a beat of a story in play, streaming as it writes. */
public interface WriteBeat {
    /**
     * A cold flow: nothing is requested until it is collected, and cancelling the collector cancels the
     * request. It ends with exactly one [WritingProgress.Written] or [WritingProgress.Failed]. Every text it
     * reports is the whole beat so far, trimmed, and never longer than [Story.MAX_BEAT_LENGTH].
     */
    public operator fun invoke(story: Story, ask: BeatAsk, model: DraftingModel): Flow<WritingProgress>
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultWriteBeat(private val generator: TextGenerator, sentences: SentenceBuilder) : WriteBeat {

    private val prompt = BeatPrompt(StoryBrief(sentences))

    override fun invoke(story: Story, ask: BeatAsk, model: DraftingModel): Flow<WritingProgress> = flow {
        if (ask is BeatAsk.Again) {
            require(story.beats.last().kind == Beat.Kind.Narration) { "Only a beat a model wrote is written again" }
        }
        val request = TextRequest(
            model = model.model,
            messages = prompt.messages(story, ask),
            tuning = model.tuning,
            sessionId = story.id.value,
        )
        val written = StringBuilder()
        var shown = ""
        generator.stream(request).collect { event ->
            when (event) {
                is TextEvent.Delta -> {
                    written.append(event.text)
                    val beat = written.toString().cleaned()
                    // Whitespace and an opening quote arrive on their own now and then; only news is reported.
                    if (beat.isNotEmpty() && beat != shown) {
                        shown = beat
                        emit(WritingProgress.Writing(beat))
                    }
                }
                is TextEvent.Reasoning -> Unit
                is TextEvent.Completed -> {
                    val beat = written.toString().cleaned()
                    emit(
                        if (beat.isEmpty()) {
                            WritingProgress.Failed(
                                DraftingError.Unusable,
                            )
                        } else {
                            WritingProgress.Written(beat)
                        },
                    )
                }
                is TextEvent.Failed -> emit(WritingProgress.Failed(DraftingError.Generation(event.error)))
            }
        }
    }

    /**
     * Trimmed, and cut at the longest beat. Models asked for bare prose still wrap it in quotes now and then;
     * quotes are only taken off when nothing inside is quoted, so dialogue keeps its marks.
     */
    private fun String.cleaned(): String {
        val text = trim()
        val inner = text.removePrefix(QUOTE).removeSuffix(QUOTE)
        val bare = if (QUOTE in inner) text else inner.trim()
        return bare.take(Story.MAX_BEAT_LENGTH).trimEnd()
    }

    private companion object {
        const val QUOTE = "\""
    }
}

/**
 * The instructions and the ask for a beat. The model reads the story's choices, the people in it, and the
 * most recent beats, so a long story never becomes one giant prompt.
 */
internal class BeatPrompt(private val brief: StoryBrief) {

    fun messages(story: Story, ask: BeatAsk): List<ChatMessage> {
        val beats = when (ask) {
            is BeatAsk.Next -> story.beats
            is BeatAsk.Again -> story.beats.dropLast(1)
        }
        return listOf(
            ChatMessage.System(SYSTEM),
            ChatMessage.User(
                buildString {
                    append(brief.of(story.setup, withPremise = true))
                    val people = story.setup.cast
                    if (people.isNotEmpty()) {
                        append("\n\nPeople in the story:")
                        people.forEach { append("\n- ${it.sheet()}") }
                    }
                    append("\n\n")
                    append(transcript(beats))
                    if (ask is BeatAsk.Again) {
                        append("\n\nThe player turned down this version of the next beat:\n")
                        append(story.beats.last().text)
                        append("\n\n")
                        append(ask.reason?.let { "What was wrong with it, in the player's words: $it" } ?: DIFFERENT)
                    }
                    ask.guide?.let {
                        append("\n\nThe player's direction for the next beat, not part of the story: $it")
                    }
                    append("\n\n$WRITE")
                },
            ),
        )
    }

    /**
     * The most recent beats that fit [RecentBeats.BUDGET], oldest first, each under a mark saying who wrote
     * it. When older beats are left out, the transcript says so, so the model does not take the window for
     * the whole story.
     */
    private fun transcript(beats: List<Beat>): String {
        val recent = RecentBeats.of(beats)
        return buildString {
            append("The story so far")
            if (recent.size < beats.size) append(", its earlier beats left out")
            append(':')
            if (recent.isEmpty()) append("\nNothing yet.")
            recent.forEach { beat ->
                append("\n\n")
                append(if (beat.kind == Beat.Kind.Turn) PLAYER_MARK else STORY_MARK)
                append('\n')
                append(beat.text)
            }
        }
    }

    private companion object {
        val SYSTEM =
            """
            You narrate an interactive story. The player plays its main character, who is addressed as "you", in the second person and the present tense.
            You write one beat at a time: one or two short paragraphs that move the story on from exactly where it stands. When the story ends on the player's turn, the beat answers that turn.
            Use only what the story gives you and what follows from it. Keep the people as they are written, and bring them in by name.
            Never decide what the player does, says, thinks or feels beyond what their turns state. Stop at a moment the player can answer.
            The player may give a direction for the next beat. Follow it, but never mention it or quote it.
            Answer with the beat alone: plain prose, no title, no headings, no preamble, no list of choices.
            """.trimIndent()

        const val STORY_MARK = "[Story]"
        const val PLAYER_MARK = "[The player's turn]"
        const val DIFFERENT = "Write a different one."
        const val WRITE = "Write the next beat."
    }
}

/**
 * Which beats a prompt carries: the most recent ones, newest kept first, up to a budget in characters. A
 * stand-in for the context engine, which will retrieve what matters from the whole story instead.
 */
internal object RecentBeats {
    /** About three thousand tokens of story: enough for the scene at hand, far from one giant prompt. */
    const val BUDGET: Int = 12_000

    /** The newest beat always goes in, however long, so the model knows where the story stands. */
    fun of(beats: List<Beat>, budget: Int = BUDGET): List<Beat> {
        var left = budget
        val kept = ArrayDeque<Beat>()
        for (beat in beats.asReversed()) {
            if (kept.isNotEmpty() && beat.text.length > left) break
            kept.addFirst(beat)
            left -= beat.text.length
        }
        return kept.toList()
    }
}
