package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.IdGenerator
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StoryDraft
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Writes new people for a story: who they are, what they want, how they sound, and what they hide. */
public interface CastPeople {
    /**
     * Up to [count] people who fit [draft] and are none of those already in it. Each has a want and a
     * [Secret.Sealed] secret, which the player never sees. Fails with [DraftingError.Unusable] when the
     * model named nobody.
     */
    public suspend operator fun invoke(
        draft: StoryDraft,
        model: DraftingModel,
        count: Int = DEFAULT_COUNT,
    ): Outcome<List<Character>, DraftingError>

    public companion object {
        public const val DEFAULT_COUNT: Int = 3
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultCastPeople(
    private val generator: StructuredGenerator,
    private val ids: IdGenerator,
    sentences: SentenceBuilder,
) : CastPeople {

    private val prompt = CastPrompt(StoryBrief(sentences))

    override suspend fun invoke(
        draft: StoryDraft,
        model: DraftingModel,
        count: Int,
    ): Outcome<List<Character>, DraftingError> {
        require(count >= 1) { "Cast at least one person, not $count" }
        val request = StructuredRequest(
            model = model.model,
            messages = prompt.messages(draft, count),
            serializer = CastPayload.serializer(),
            descriptions = CastPayload.DESCRIPTIONS,
            examples = listOf(CastPayload.EXAMPLE),
            tuning = model.tuning,
            sessionId = draft.id.value,
        )
        val cast = when (val answer = generator.generate(request)) {
            is Outcome.Failure -> return Outcome.Failure(DraftingError.Generation(answer.error))
            is Outcome.Success -> answer.value
        }
        val taken = draft.cast.mapNotNullTo(HashSet()) { it.character.name?.lowercase() }
        val people = cast.people
            .mapNotNull { it.toCharacter() }
            .distinctBy { it.name?.lowercase() }
            .filterNot { it.name?.lowercase() in taken }
            .take(count)
        return if (people.isEmpty()) Outcome.Failure(DraftingError.Unusable) else Outcome.Success(people)
    }

    /** Someone without a name cannot be put in a sentence, so they are left out. */
    private fun PersonPayload.toCharacter(): Character? {
        val name = name.clean() ?: return null
        return Character(
            id = CharacterId(ids.next()),
            role = role.clean(),
            name = name,
            age = age.clean(),
            gender = gender.clean(),
            appearance = appearance.clean(),
            personality = personality.mapNotNull { it.clean() },
            speech = speech.clean(),
            voiceLine = voiceLine.clean()?.removeSurrounding("\""),
            wants = wants.clean(),
            flaw = flaw.clean(),
            habit = habit.clean(),
            goodAt = goodAt.clean(),
            secret = secret.clean()?.let(Secret::Sealed) ?: Secret.None,
        )
    }

    private fun String.clean(): String? = trim().takeIf(String::isNotEmpty)
}

/** The instructions and the ask for new people. */
internal class CastPrompt(private val brief: StoryBrief) {

    fun messages(draft: StoryDraft, count: Int): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(brief.of(draft, withPremise = true))
                val already = draft.cast.mapNotNull { it.character.tag() }
                if (already.isNotEmpty()) {
                    append("\n\nAlready in the story, so cast someone else:")
                    already.forEach { append("\n- $it") }
                }
                append("\n\nCast ${count.people()}.")
            },
        ),
    )

    private fun Int.people(): String = if (this == 1) "one person" else "$this people"

    private companion object {
        val SYSTEM =
            """
            You cast the people the player meets in an interactive story. The player plays its main character, who is addressed as "you"; never cast them.
            Each person is distinct from the others, belongs in this story's world, and has a name that fits it.
            Give each one something concrete they want, a habit with a trigger ("when …, she …"), one line they would say, and a secret.
            The secret is never shown to the player. Make it one that would change the story if it came out.
            Keep every field to a phrase or one short sentence.
            """.trimIndent()
    }
}

/** What the model answers. Every field is required, so the schema stays valid for strict structured outputs. */
@Serializable
@SerialName("Cast")
internal data class CastPayload(val people: List<PersonPayload>) {
    companion object {
        val DESCRIPTIONS: Map<String, String> = mapOf(
            "Cast.people" to "The people, in the order they matter to the story",
            "Person.role" to "What they are to the story, lower case, e.g. \"reliquary keeper\"",
            "Person.name" to "Their name",
            "Person.age" to "Their age, in a word or a number",
            "Person.gender" to "Their gender, in a word",
            "Person.appearance" to "How they look, in one sentence",
            "Person.personality" to "Two or three traits, one or two words each",
            "Person.speech" to "How they speak",
            "Person.voiceLine" to "One line of their dialogue, without quotation marks",
            "Person.wants" to "What they want, concretely",
            "Person.flaw" to "The flaw that gets in their way",
            "Person.habit" to "A habit with its trigger",
            "Person.goodAt" to "What they are good at",
            "Person.secret" to "What they hide; never shown to the player",
        )

        val EXAMPLE = CastPayload(
            listOf(
                PersonPayload(
                    role = "reliquary keeper",
                    name = "Maren Kask",
                    age = "fifty",
                    gender = "woman",
                    appearance = "Ink-stained fingers and a coat two winters too thin.",
                    personality = listOf("patient", "dry"),
                    speech = "Short sentences, never a question she can avoid",
                    voiceLine = "Saints don't haggle. I do.",
                    wants = "To buy back the relic her brother sold",
                    flaw = "Trusts paper more than people",
                    habit = "Counts the coins twice when she is lying",
                    goodAt = "Spotting a forged seal",
                    secret = "She forged the relic herself, years ago",
                ),
            ),
        )
    }
}

@Serializable
@SerialName("Person")
internal data class PersonPayload(
    val role: String,
    val name: String,
    val age: String,
    val gender: String,
    val appearance: String,
    val personality: List<String>,
    val speech: String,
    val voiceLine: String,
    val wants: String,
    val flaw: String,
    val habit: String,
    val goodAt: String,
    val secret: String,
)
