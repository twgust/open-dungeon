package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.opendungeon.domain.story.Register
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Writes new seeds once the ones that come with the app are used up. */
public interface WriteSeeds {
    /**
     * Up to [count] new seeds for [shelf], none of them one of [avoiding]. With no shelf the seeds mix
     * every shelf that is not [opt-in][Register.optIn]. Seeds the model got wrong, such as a blank the
     * template never uses, are left out; [DraftingError.Unusable] when none is left.
     */
    public suspend operator fun invoke(
        model: DraftingModel,
        shelf: Register?,
        avoiding: List<Seed>,
        count: Int = DEFAULT_COUNT,
    ): Outcome<List<Seed>, DraftingError>

    public companion object {
        public const val DEFAULT_COUNT: Int = 4
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultWriteSeeds(private val generator: StructuredGenerator) : WriteSeeds {

    override suspend fun invoke(
        model: DraftingModel,
        shelf: Register?,
        avoiding: List<Seed>,
        count: Int,
    ): Outcome<List<Seed>, DraftingError> {
        require(count >= 1) { "Write at least one seed, not $count" }
        val request = StructuredRequest(
            model = model.model,
            messages = SeedsPrompt.messages(shelf, avoiding, count),
            serializer = SeedsPayload.serializer(),
            descriptions = SeedsPayload.DESCRIPTIONS,
            examples = listOf(SeedsPayload.EXAMPLE),
            tuning = model.tuning,
        )
        val payload = when (val answer = generator.generate(request)) {
            is Outcome.Failure -> return Outcome.Failure(DraftingError.Generation(answer.error))
            is Outcome.Success -> answer.value
        }
        val known = avoiding.mapTo(HashSet()) { it.template.lowercase() }
        // A seed asked for one shelf is on that shelf, whatever the model called it; a mixed one must
        // never land on a shelf the player has to choose.
        val seeds = payload.seeds
            .mapNotNull { it.toSeed(shelf) }
            .filter { shelf != null || !it.shelf.optIn }
            .filter { known.add(it.template.lowercase()) }
            .take(count)
        return if (seeds.isEmpty()) Outcome.Failure(DraftingError.Unusable) else Outcome.Success(seeds)
    }
}

/** The instructions and the ask for new seeds. */
internal object SeedsPrompt {

    fun messages(shelf: Register?, avoiding: List<Seed>, count: Int): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(
                    when (shelf) {
                        null -> "Mix the shelves, genres and situations, but write nothing for After dark. $SUGGESTIVE"
                        Register.AfterDark -> "Write only After dark seeds. $FRANK"
                        else -> "Write only ${shelf.title} seeds. $SUGGESTIVE"
                    },
                )
                append("\n\n")
                if (avoiding.isNotEmpty()) {
                    append("The player has seen these, so write different ones:")
                    avoiding.forEach { append("\n- ${it.template}") }
                    append("\n\n")
                }
                append(if (count == 1) "Write one seed." else "Write $count seeds.")
            },
        ),
    )

    private const val SUGGESTIVE = "Keep them playable at any content limit: suggestive at most, never explicit."

    private const val FRANK =
        "The player chose explicit stories, so write these as frankly as they call for. " +
            "Everyone is an adult, and everything is consensual."

    /** A shelf's name as the model is told it. */
    private val Register.title: String
        get() = when (this) {
            Register.Everyday -> "Everyday"
            Register.AfterDark -> "After dark"
            Register.Dark -> "Dark"
            Register.Fantastical -> "Fantastical"
            Register.OtherWorlds -> "Other worlds"
            Register.Historical -> "Historical"
        }

    /** What a shelf holds. */
    private val Register.holds: String
        get() = when (this) {
            Register.Everyday ->
                "ordinary lives, work, family, friends and love, with small, real stakes; no crime, magic or monsters"
            Register.AfterDark -> "ordinary adult lives with sex at the centre: affairs, hookups, kinks, sex work"
            Register.Dark -> "crime, horror, mystery and dread"
            Register.Fantastical -> "magic, myth, swords and the uncanny"
            Register.OtherWorlds -> "science fiction, futures, and what comes after the fall"
            Register.Historical -> "real past eras, courts and intrigue"
        }

    private val SYSTEM =
        """
        You write seeds for an interactive story: a one-line premise with blanks, which the player fills by tapping for other words. The player plays the main character, addressed as "you".
        Write the template as one short sentence, at most 120 characters, with one to three blanks. Write each blank as its key in braces, such as {who}; keys are short lower case words.
        Every blank must read well with any of its fills, so put articles inside the fills ("a live goose"), not in the template.
        Give every blank six fills that fit it and differ from each other, best first. Keep each fill under 40 characters.
        The counterpart is the person the player meets first, written the way the sentence names them, such as "your {who}" or "the estate agent".
        Every seed belongs on one shelf, the kind of life it is about:
        """.trimIndent() + Register.entries.joinToString(separator = "") {
            "\n- ${it.title}: ${it.holds}."
        }
}

/** What the model answers. Every field is required, so the schema stays valid for strict structured outputs. */
@Serializable
@SerialName("Seeds")
internal data class SeedsPayload(val seeds: List<SeedPayload>) {
    companion object {
        val DESCRIPTIONS: Map<String, String> = mapOf(
            "Seeds.seeds" to "The seeds, each a different situation",
            "Seed.genre" to "The genre, in one or two words, capitalised: \"Comedy\"",
            "Seed.tone" to "How it is told, as an adverb or short phrase: \"wryly\", \"with dread\"",
            "Seed.template" to "One sentence with each blank written as {key}",
            "Seed.counterpart" to "The person the player meets first, as the sentence names them: \"your {who}\"",
            "Seed.blanks" to "Every blank in the template, once each",
            "Seed.shelf" to "The shelf the seed belongs on",
            "Blank.key" to "The blank's key as written in the template, lower case letters only",
            "Blank.kind" to "What the blank asks for",
            "Blank.fills" to "Six fills that fit the blank, best first",
        )

        val EXAMPLE = SeedsPayload(
            listOf(
                SeedPayload(
                    genre = "Comedy",
                    tone = "playfully",
                    template = "Caught {act} by your {who}.",
                    counterpart = "your {who}",
                    shelf = ShelfPayload.Everyday,
                    blanks = listOf(
                        BlankPayload(
                            key = "act",
                            kind = BlankKindPayload.Act,
                            fills = listOf(
                                "forging a doctor’s note",
                                "selling the family silver",
                                "reading their diary",
                                "wearing their wedding dress",
                                "rehearsing a breakup speech",
                                "teaching the parrot to swear",
                            ),
                        ),
                        BlankPayload(
                            key = "who",
                            kind = BlankKindPayload.Person,
                            fills = listOf("grandmother", "parole officer", "ex", "boss", "priest", "younger sister"),
                        ),
                    ),
                ),
            ),
        )
    }
}

@Serializable
@SerialName("Seed")
internal data class SeedPayload(
    val genre: String,
    val tone: String,
    val template: String,
    val counterpart: String,
    val shelf: ShelfPayload,
    val blanks: List<BlankPayload>,
) {
    /** This seed, on [asked] when the seeds were asked for one shelf, else on the shelf it names. */
    fun toSeed(asked: Register? = null): Seed? = Seed.of(
        genre = genre,
        tone = tone,
        template = template,
        counterpart = counterpart,
        blanks = blanks.map { Triple(it.key, it.kind.toKind(), it.fills) },
        shelf = asked ?: shelf.register,
    )
}

/** A shelf as the schema names it, apart from [Register] so a rename there never changes the schema. */
@Serializable
@SerialName("Shelf")
internal enum class ShelfPayload(val register: Register) {
    @SerialName("everyday")
    Everyday(Register.Everyday),

    @SerialName("after dark")
    AfterDark(Register.AfterDark),

    @SerialName("dark")
    Dark(Register.Dark),

    @SerialName("fantastical")
    Fantastical(Register.Fantastical),

    @SerialName("other worlds")
    OtherWorlds(Register.OtherWorlds),

    @SerialName("historical")
    Historical(Register.Historical),
}

@Serializable
@SerialName("Blank")
internal data class BlankPayload(val key: String, val kind: BlankKindPayload, val fills: List<String>)

/** A blank's kind as the schema names it, apart from [Seed.Kind] so a rename there never changes the schema. */
@Serializable
@SerialName("BlankKind")
internal enum class BlankKindPayload {
    @SerialName("person")
    Person,

    @SerialName("thing")
    Thing,

    @SerialName("act")
    Act,

    @SerialName("place")
    Place,
    ;

    fun toKind(): Seed.Kind = when (this) {
        Person -> Seed.Kind.Person
        Thing -> Seed.Kind.Thing
        Act -> Seed.Kind.Act
        Place -> Seed.Kind.Place
    }
}

/** How a kind is named in prompts. */
internal val Seed.Kind.word: String
    get() = when (this) {
        Seed.Kind.Person -> "a person"
        Seed.Kind.Thing -> "a thing"
        Seed.Kind.Act -> "something done"
        Seed.Kind.Place -> "a place"
    }
