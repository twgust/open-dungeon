package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.core.model.Tuning
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.GenerationError
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.opendungeon.domain.generation.TextEvent
import dev.opendungeon.domain.generation.TextGenerator
import dev.opendungeon.domain.generation.TextRequest
import dev.opendungeon.domain.openrouter.testing.aiModel
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.io.File
import kotlin.test.assertEquals

/** Streams [events] for every request and keeps the requests. */
internal class ScriptedTextGenerator(private val events: List<TextEvent>) : TextGenerator {
    val requests = mutableListOf<TextRequest>()

    override fun stream(request: TextRequest): Flow<TextEvent> {
        requests += request
        return events.asFlow()
    }
}

/** Answers every request with [answer] and keeps the requests. */
internal class ScriptedStructuredGenerator(private val answer: Outcome<Any, GenerationError>) : StructuredGenerator {
    val requests = mutableListOf<StructuredRequest<*>>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> generate(request: StructuredRequest<T>): Outcome<T, GenerationError> {
        requests += request
        return answer as Outcome<T, GenerationError>
    }
}

internal class CountingIds(private val prefix: String) : IdGenerator {
    private var next = 0

    override fun next(): String = "$prefix-${++next}"
}

internal val drafter = DraftingModel(aiModel("acme/storyteller"), Tuning(temperature = 0.9))

/** A draft with every step before the premise filled, the way a player leaves it. */
internal val fullDraft = StoryDraft(
    id = DraftId("draft-7"),
    genres = listOf(Phrase.Offered("Gothic"), Phrase.Own("salt-punk")),
    twist = Phrase.Offered("a heist"),
    protagonist = Character(
        id = CharacterId("you"),
        role = "a disgraced smuggler",
        wants = "To be forgiven",
        flaw = "Lies easily",
        goodAt = "Picking locks",
    ),
    tone = Phrase.Offered("tenderly"),
    setting = Phrase.Offered("a salt-war border town"),
    limits = ContentLimits(
        ContentLimits.Desire.AllTheWay,
        ContentLimits.Violence.Offstage,
        note = "no harm to animals",
    ),
)

/**
 * Compares [actual] with the golden file [name]. With `-PupdateGoldens` the file is written instead, so a
 * deliberate prompt change is one command and a reviewed diff.
 */
internal fun assertGolden(name: String, actual: String) {
    val file = File(System.getProperty("goldens.dir"), "$name.txt")
    if (System.getProperty("goldens.update").toBoolean()) {
        file.parentFile.mkdirs()
        file.writeText(actual)
        return
    }
    check(file.exists()) { "No golden file $name.txt; run the tests with -PupdateGoldens to write it" }
    assertEquals(file.readText(), actual, "the prompt differs from $name.txt")
}

/** Messages as a reviewer reads them in a golden file. */
internal fun List<ChatMessage>.transcript(): String = joinToString("\n\n") { message ->
    val role = when (message) {
        is ChatMessage.System -> "system"
        is ChatMessage.User -> "user"
        is ChatMessage.Assistant -> "assistant"
    }
    "## $role\n${message.text}"
} + "\n"
