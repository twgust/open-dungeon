package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StorageError
import dev.opendungeon.domain.story.Story
import dev.opendungeon.domain.story.StoryId
import dev.opendungeon.domain.story.StoryRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/** Gives an untitled story a title, written by the drafting model from how the story begins. */
public interface NameStory {
    /**
     * Names the story with [id] if it has no title, and leaves it as it is otherwise, including when the
     * player names it while the model writes. A story that is gone needs no name and succeeds.
     */
    public suspend operator fun invoke(id: StoryId): Outcome<Unit, NamingError>
}

/** Why a story could not be named. Naming is background work: the story is only left untitled. */
public sealed interface NamingError {
    public data class Drafting(val error: DraftingError) : NamingError

    public data class Storage(val error: StorageError) : NamingError
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultNameStory(
    private val stories: StoryRepository,
    private val drafting: ResolveDraftingModel,
    private val generator: TextGenerator,
    sentences: SentenceBuilder,
) : NameStory {

    private val prompt = TitlePrompt(StoryBrief(sentences))

    override suspend fun invoke(id: StoryId): Outcome<Unit, NamingError> {
        val story = when (val read = stories.story(id)) {
            is Outcome.Failure -> return Outcome.Failure(NamingError.Storage(read.error))
            is Outcome.Success -> read.value
        }
        if (story == null || story.title != null) return Outcome.Success(Unit)
        val model = drafting().first() ?: return Outcome.Failure(NamingError.Drafting(DraftingError.NoModel))
        val title = when (val written = write(story, model)) {
            is Outcome.Failure -> return Outcome.Failure(NamingError.Drafting(written.error))
            is Outcome.Success -> written.value
        }
        return when (val named = stories.nameIfUntitled(id, title)) {
            is Outcome.Failure -> Outcome.Failure(NamingError.Storage(named.error))
            is Outcome.Success -> named
        }
    }

    private suspend fun write(story: Story, model: DraftingModel): Outcome<String, DraftingError> {
        val request = TextRequest(
            model = model.model,
            messages = prompt.messages(story),
            tuning = model.tuning,
            sessionId = story.id.value,
        )
        val written = StringBuilder()
        var failure: DraftingError? = null
        generator.stream(request).collect { event ->
            when (event) {
                is TextEvent.Delta -> written.append(event.text)
                is TextEvent.Failed -> failure = DraftingError.Generation(event.error)
                is TextEvent.Reasoning, is TextEvent.Completed -> Unit
            }
        }
        failure?.let { return Outcome.Failure(it) }
        return Titles.clean(written.toString())?.let { Outcome.Success(it) } ?: Outcome.Failure(DraftingError.Unusable)
    }
}

/** How a model's answer becomes a title. */
internal object Titles {

    /**
     * The first line of [answer], without the wrapping models add even when told not to: a "Title:" label,
     * quotes, emphasis and a closing full stop. Cut at a word to [Story.MAX_TITLE_LENGTH]. `null` when
     * nothing is left.
     */
    fun clean(answer: String): String? {
        val line = answer.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty) ?: return null
        val bare = line
            .replace(LABEL, "")
            .trim { it.isWhitespace() || it in WRAPPING }
            .removeSuffix(".")
            .replace(SPACES, " ")
            .trim()
        return bare.cut().ifEmpty { null }
    }

    private fun String.cut(): String {
        if (length <= Story.MAX_TITLE_LENGTH) return this
        val head = take(Story.MAX_TITLE_LENGTH + 1)
        val atWord = head.substringBeforeLast(' ', missingDelimiterValue = "").trimEnd()
        return atWord.ifEmpty { take(Story.MAX_TITLE_LENGTH) }
    }

    private val LABEL = Regex("""^\W*title\W*:\s*""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")
    private const val WRAPPING = "\"'“”‘’*_#`"
}

/** The instructions and the ask for a story's title. */
internal class TitlePrompt(private val brief: StoryBrief) {

    fun messages(story: Story): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(brief.of(story.setup, withPremise = true))
                append("\n\nHow it begins:\n")
                append(story.beats.first().text.take(MAX_OPENING))
                append("\n\nName the story.")
            },
        ),
    )

    private companion object {
        /** The start of the opening says enough; the rest only costs tokens. */
        const val MAX_OPENING = 2_000

        val SYSTEM =
            """
            You name interactive stories. Write a title for the story you are given: two to five words that fit its genre and tone and promise what it is about, without giving away anything that has not happened yet.
            Answer with the title alone, on one line: no quotation marks, no subtitle, no preamble.
            """.trimIndent()
    }
}
