package dev.opendungeon.domain.drafting

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.generation.ChatMessage
import dev.opendungeon.domain.generation.StructuredGenerator
import dev.opendungeon.domain.generation.StructuredRequest
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.SentenceBuilder
import dev.opendungeon.domain.story.StoryDraft
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The slop guard for names. A prompt alone does not keep a model off its favourite names, so every person a
 * model writes passes through here: worn names are found against [WornNames] and the model renames those
 * people, keeping everything else about them.
 */
public interface ReplaceWornNames {
    /**
     * [people], just written for [draft], in the same order, with every worn name replaced by a fresh one
     * that fits the story. Nobody with a fresh name is touched, and no request is made when nobody needs it.
     *
     * A worn name the player wrote themselves, anywhere in [playerWords], is theirs and stays. A new name
     * repeats nobody in [draft] or in [people]. Where the old name appears in a person's other lines, the
     * new one takes its place.
     *
     * Fails with [DraftingError.Unusable] when a worn name is still there after [ATTEMPTS] requests, and
     * with [DraftingError.Generation] when a request fails.
     */
    public suspend operator fun invoke(
        people: List<Character>,
        draft: StoryDraft,
        model: DraftingModel,
        playerWords: List<String> = emptyList(),
    ): Outcome<List<Character>, DraftingError>

    public companion object {
        /** One request and one retry: a model that misses twice will not stop. */
        public const val ATTEMPTS: Int = 2
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class DefaultReplaceWornNames(
    private val generator: StructuredGenerator,
    private val worn: WornNames,
    guide: NamingGuide,
    sentences: SentenceBuilder,
) : ReplaceWornNames {

    private val prompt = RenamePrompt(StoryBrief(sentences), guide)

    override suspend fun invoke(
        people: List<Character>,
        draft: StoryDraft,
        model: DraftingModel,
        playerWords: List<String>,
    ): Outcome<List<Character>, DraftingError> {
        val theirs = playerWords.flatMapTo(HashSet(), worn::foundIn)

        fun Character.isWorn(): Boolean = name?.let { (worn.foundIn(it) - theirs).isNotEmpty() } == true

        val inDraft = (draft.cast.map { it.character } + listOfNotNull(draft.protagonist)).mapNotNull { it.name }
        val turnedDown = LinkedHashSet<String>()
        var current = people
        repeat(ReplaceWornNames.ATTEMPTS) {
            val due = current.filter { it.isWorn() }
            if (due.isEmpty()) return Outcome.Success(current)
            due.mapNotNullTo(turnedDown) { it.name }
            val taken = inDraft + current.filterNot { it in due }.mapNotNull { it.name }
            val request = StructuredRequest(
                model = model.model,
                messages = prompt.messages(draft, due, turnedDown, taken),
                serializer = RenamesPayload.serializer(),
                descriptions = RenamesPayload.DESCRIPTIONS,
                examples = listOf(RenamesPayload.EXAMPLE),
                tuning = model.tuning,
                sessionId = draft.id.value,
            )
            val proposals = when (val answer = generator.generate(request)) {
                is Outcome.Failure -> return Outcome.Failure(DraftingError.Generation(answer.error))
                is Outcome.Success -> answer.value.names.associate { it.person to it.name.trim() }
            }
            val used = taken.mapTo(HashSet(), String::lowercase)
            current = current.map { person ->
                val number = due.indexOf(person) + 1
                val proposal = proposals[number]?.takeIf(String::isNotEmpty)
                when {
                    number == 0 || proposal == null -> person
                    (worn.foundIn(proposal) - theirs).isNotEmpty() -> person.also { turnedDown += proposal }
                    // Taken names are listed as taken on the next try, not as turned down.
                    !used.add(proposal.lowercase()) -> person
                    else -> person.renamed(proposal)
                }
            }
        }
        return if (current.none { it.isWorn() }) Outcome.Success(current) else Outcome.Failure(DraftingError.Unusable)
    }
}

/**
 * Gives [this] the name [to], and puts it wherever their lines use the old one: the whole name, and its
 * first and last words, so "Kael" in a secret about Kael Voss follows the rename too.
 */
internal fun Character.renamed(to: String): Character {
    val old = name ?: return copy(name = to)
    val oldWords = old.split(WHITESPACE)
    val newWords = to.split(WHITESPACE)
    val swaps = buildMap {
        put(old, to)
        if (oldWords.size > 1) {
            put(oldWords.first(), newWords.first())
            put(oldWords.last(), newWords.last())
        }
    }.filter { (from, into) -> from != into && from.count(Char::isLetter) >= MIN_SWAP }
    if (swaps.isEmpty()) return copy(name = to)
    // One pass, longest first, so the whole name is swapped before its words and nothing is swapped twice.
    val pattern = swaps.keys.sortedByDescending(String::length)
        .joinToString("|", prefix = """(?<![\p{L}\p{N}])(""", postfix = """)(?![\p{L}\p{N}])""") { Regex.escape(it) }
        .toRegex()

    fun String.swapped(): String = pattern.replace(this) { swaps.getValue(it.value) }

    fun String?.swapped(): String? = this?.swapped()
    return copy(
        name = to,
        role = role.swapped(),
        appearance = appearance.swapped(),
        personality = personality.map { it.swapped() },
        speech = speech.swapped(),
        voiceLine = voiceLine.swapped(),
        wants = wants.swapped(),
        flaw = flaw.swapped(),
        habit = habit.swapped(),
        goodAt = goodAt.swapped(),
        secret = when (val secret = secret) {
            Secret.None -> Secret.None
            is Secret.Known -> Secret.Known(secret.text.swapped())
            is Secret.Sealed -> Secret.Sealed(secret.text.swapped())
        },
    )
}

private val WHITESPACE = Regex("""\s+""")

/** Words shorter than this, such as "Jo" or "Dr", are too likely to be something else to swap. */
private const val MIN_SWAP = 3

/** The instructions and the ask for new names. */
internal class RenamePrompt(private val brief: StoryBrief, private val guide: NamingGuide) {

    fun messages(
        draft: StoryDraft,
        people: List<Character>,
        turnedDown: Collection<String>,
        taken: Collection<String>,
    ): List<ChatMessage> = listOf(
        ChatMessage.System(SYSTEM),
        ChatMessage.User(
            buildString {
                append(brief.of(draft, withPremise = true))
                append("\n\n")
                append(guide.notes(draft))
                append("\n\nRename these people:")
                people.forEachIndexed { index, person -> append("\n${index + 1}. ${person.sheet()}") }
                append("\n\nTurned down, so never use: ${turnedDown.joinToString(", ")}")
                if (taken.isNotEmpty()) append("\nAlready in the story: ${taken.joinToString(", ")}")
                append("\n\nGive each of them a new name.")
            },
        ),
    )

    private companion object {
        val SYSTEM =
            """
            You rename people in an interactive story. Their names were turned down as the stock names models overuse.
            Give each person a new name that fits who they are and the story's world, following the naming notes.
            Keep the shape of the old name: a surname only if it had one, and any title or nickname it carried.
            Never use a name that was turned down, one already in the story, or one you give someone else.
            """.trimIndent()
    }
}

/** What the model answers. Every field is required, so the schema stays valid for strict structured outputs. */
@Serializable
@SerialName("Renames")
internal data class RenamesPayload(val names: List<RenamePayload>) {
    companion object {
        val DESCRIPTIONS: Map<String, String> = mapOf(
            "Renames.names" to "One new name for each person to rename",
            "Rename.person" to "The person's number in the list",
            "Rename.name" to "Their new name",
        )

        val EXAMPLE = RenamesPayload(listOf(RenamePayload(person = 1, name = "Maren Kask")))
    }
}

@Serializable
@SerialName("Rename")
internal data class RenamePayload(val person: Int, val name: String)
