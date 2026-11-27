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

/**
 * Casts a story that started from a seed: the player's character and the person they meet first, from
 * whatever the player said about either, inventing the rest.
 */
public interface CastSeed {
    /**
     * The two people of [draft], whose premise is the seed's sentence. [you] and [them] are the player's
     * own words about each, or `null` to leave them to the model; [counterpart] is how the sentence names
     * the other person, such as "your grandmother".
     *
     * The player's character has no secret; the other person has a [Secret.Sealed] one. Fails with
     * [DraftingError.Unusable] when the model left either without a name.
     */
    public suspend operator fun invoke(
        draft: StoryDraft,
        counterpart: String,
        you: String?,
        them: String?,
        model: DraftingModel,
    ): Outcome<SeedCast, DraftingError>
}

/** The people a seeded story starts with. */
public data class SeedCast(val you: Character, val them: Character)

@Inject
@ContributesBinding(AppScope::class)
public class DefaultCastSeed(
    private val generator: StructuredGenerator,
    private val ids: IdGenerator,
    sentences: SentenceBuilder,
) : CastSeed {

    private val prompt = SeedCastPrompt(StoryBrief(sentences))

    override suspend fun invoke(
        draft: StoryDraft,
        counterpart: String,
        you: String?,
        them: String?,
        model: DraftingModel,
    ): Outcome<SeedCast, DraftingError> {
        val request = StructuredRequest(
            model = model.model,
            messages = prompt.messages(
                draft,
                counterpart,
                you?.takeIf(String::isNotBlank),
                them?.takeIf(String::isNotBlank),
            ),
            serializer = SeedCastPayload.serializer(),
            descriptions = SeedCastPayload.DESCRIPTIONS,
            examples = listOf(SeedCastPayload.EXAMPLE),
            tuning = model.tuning,
            sessionId = draft.id.value,
        )
        val payload = when (val answer = generator.generate(request)) {
            is Outcome.Failure -> return Outcome.Failure(DraftingError.Generation(answer.error))
            is Outcome.Success -> answer.value
        }
        val player = payload.you.toCharacter(secret = false) ?: return Outcome.Failure(DraftingError.Unusable)
        val other = payload.them.toCharacter(secret = true) ?: return Outcome.Failure(DraftingError.Unusable)
        return Outcome.Success(SeedCast(you = player, them = other))
    }

    /** Someone without a name cannot be put in a sentence, so the answer is not used. */
    private fun PersonPayload.toCharacter(secret: Boolean): Character? {
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
            // The player's own card never has a secret, whatever the model wrote there.
            secret = this.secret.clean()?.takeIf { secret }?.let(Secret::Sealed) ?: Secret.None,
        )
    }

    private fun String.clean(): String? = trim().takeIf(String::isNotEmpty)
}

/** The instructions and the ask for a seeded story's two people. */
internal class SeedCastPrompt(private val brief: StoryBrief) {

    fun messages(draft: StoryDraft, counterpart: String, you: String?, them: String?): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(brief.of(draft, withPremise = true))
                append("\n\nYou, the player's character: ")
                append(you?.let { "the player says \"$it\"" } ?: INVENT)
                append("\nThem, $counterpart: ")
                append(them?.let { "the player says \"$it\"" } ?: INVENT)
                append("\n\nCast you and them.")
            },
        ),
    )

    private companion object {
        const val INVENT = "invent them to fit the premise"

        val SYSTEM =
            """
            You cast the two people an interactive story starts with: the player's character, addressed as "you", and the person the premise names, "them".
            Where the player describes a person, keep everything they said and fill in the rest so it fits. Where they do not, invent someone who fits the premise.
            Give both a name that fits the story's world, something concrete they want, a habit with a trigger ("when …, she …"), and one line they would say.
            Give them a secret that would change the story if it came out; it is never shown to the player. The player's character has no secret: leave it empty.
            Keep every field to a phrase or one short sentence.
            """.trimIndent()
    }
}

/** What the model answers. Every field is required, so the schema stays valid for strict structured outputs. */
@Serializable
@SerialName("SeedCast")
internal data class SeedCastPayload(val you: PersonPayload, val them: PersonPayload) {
    companion object {
        val DESCRIPTIONS: Map<String, String> = CastPayload.DESCRIPTIONS + mapOf(
            "SeedCast.you" to "The player's character; their secret is empty",
            "SeedCast.them" to "The person the premise names",
        )

        val EXAMPLE = SeedCastPayload(
            you = PersonPayload(
                role = "night-shift nurse",
                name = "Noor Haddad",
                age = "thirty-one",
                gender = "woman",
                appearance = "Scrubs under a raincoat, hair in a pencil-pinned knot.",
                personality = listOf("dry", "tired", "kind"),
                speech = "Quick, with jokes she doesn't wait to land",
                voiceLine = "I can explain, and most of it is even true.",
                wants = "Three days off without anyone asking why",
                flaw = "Lies to avoid a fuss",
                habit = "Hums when she is cornered",
                goodAt = "Calming people down",
                secret = "",
            ),
            them = CastPayload.EXAMPLE.people.single(),
        )
    }
}
