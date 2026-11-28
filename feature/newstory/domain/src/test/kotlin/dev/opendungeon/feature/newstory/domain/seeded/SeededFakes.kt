package dev.opendungeon.feature.newstory.domain.seeded

import dev.opendungeon.core.model.Outcome
import dev.opendungeon.domain.drafting.CastSeed
import dev.opendungeon.domain.drafting.DraftingError
import dev.opendungeon.domain.drafting.DraftingModel
import dev.opendungeon.domain.drafting.FillBlank
import dev.opendungeon.domain.drafting.Seed
import dev.opendungeon.domain.drafting.SeedCast
import dev.opendungeon.domain.drafting.WriteSeeds
import dev.opendungeon.domain.story.Character
import dev.opendungeon.domain.story.CharacterId
import dev.opendungeon.domain.story.Register
import dev.opendungeon.domain.story.Secret
import dev.opendungeon.domain.story.StoryDraft
import kotlinx.coroutines.CompletableDeferred

internal fun seed(
    template: String,
    vararg blanks: Pair<String, List<String>>,
    counterpart: String = "your {who}",
    shelf: Register = Register.Everyday,
) = requireNotNull(
    Seed.of(
        genre = "Comedy",
        tone = "wryly",
        template = template,
        counterpart = counterpart,
        blanks = blanks.map { (key, fills) -> Triple(key, Seed.Kind.Person, fills) },
        shelf = shelf,
    ),
)

internal val caught = seed("Caught lying by your {who}.", "who" to listOf("grandmother", "boss"))
internal val knocking = seed("The {who} keeps knocking.", "who" to listOf("child"), counterpart = "the {who}")

internal class FixedDeck(override val seeds: List<Seed>) : SeedDeck

/** One call the test answers by hand. */
internal class Call<A, T>(val args: A) {
    val answer = CompletableDeferred<T>()
}

/** Seeds, words and people the test writes by hand: every call waits for its answer. */
internal class ScriptedSeedWork :
    WriteSeeds,
    FillBlank,
    CastSeed {

    val seedCalls = mutableListOf<Call<List<Seed>, Outcome<List<Seed>, DraftingError>>>()

    /** The shelf each call to [seedCalls] asked for, in the same order. */
    val seedShelves = mutableListOf<Register?>()
    val fillCalls = mutableListOf<Call<Pair<Seed, String>, Outcome<Seed, DraftingError>>>()
    val castCalls = mutableListOf<Call<CastArgs, Outcome<SeedCast, DraftingError>>>()

    data class CastArgs(val draft: StoryDraft, val counterpart: String, val you: String?, val them: String?)

    override suspend fun invoke(
        model: DraftingModel,
        shelf: Register?,
        avoiding: List<Seed>,
        count: Int,
    ): Outcome<List<Seed>, DraftingError> {
        seedShelves += shelf
        return Call<List<Seed>, Outcome<List<Seed>, DraftingError>>(avoiding).also { seedCalls += it }.answer.await()
    }

    override suspend fun invoke(seed: Seed, key: String, model: DraftingModel): Outcome<Seed, DraftingError> =
        Call<Pair<Seed, String>, Outcome<Seed, DraftingError>>(seed to key).also { fillCalls += it }.answer.await()

    override suspend fun invoke(
        draft: StoryDraft,
        counterpart: String,
        you: String?,
        them: String?,
        model: DraftingModel,
    ): Outcome<SeedCast, DraftingError> =
        Call<CastArgs, Outcome<SeedCast, DraftingError>>(CastArgs(draft, counterpart, you, them))
            .also { castCalls += it }
            .answer
            .await()
}

internal val noor = Character(CharacterId("you"), role = "night-shift nurse", name = "Noor")
internal val salma = Character(CharacterId("them"), role = "grandmother", name = "Salma", secret = Secret.Sealed("x"))
internal val twoPeople = SeedCast(you = noor, them = salma)
