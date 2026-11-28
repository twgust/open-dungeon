package dev.opendungeon.feature.newstory.domain.seeded

import dev.opendungeon.domain.drafting.Seed
import dev.opendungeon.domain.story.Register
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/** The seeds that come with the app: instant, free, and dealt before a model writes any. */
public interface SeedDeck {
    public val seeds: List<Seed>
}

/**
 * Reads the seeds bundled with this module. They are data, not code, so they can be edited without
 * touching a rule. An edit can break one, so the deck holds only the whole ones: a broken seed is left
 * out rather than taking creation down with it, and with none left the wizard asks a model for seeds as
 * it does once the deck is used up. A broken edit fails loudly in this module's tests instead.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BundledSeedDeck : SeedDeck {

    override val seeds: List<Seed> by lazy { read()?.let(SeedFile::seedsIn).orEmpty() }

    private fun read(): String? = try {
        javaClass.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
    } catch (_: IOException) {
        null
    }

    internal companion object {
        const val RESOURCE = "/dev/opendungeon/feature/newstory/domain/seeds.json"
    }
}

/** The bundled seeds file, read one seed at a time so a broken seed costs only itself. */
internal object SeedFile {

    /** The whole seeds in [text], in order: none when it is not a seeds file, and only whole ones when it is. */
    fun seedsIn(text: String): List<Seed> = entriesIn(text).mapNotNull(::seedOf)

    /** Every entry of the file, whole or not, so a test can tell a seed left out from one never written. */
    fun entriesIn(text: String): List<JsonElement> {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return emptyList()
        }
        return ((root as? JsonObject)?.get(SEEDS) as? JsonArray).orEmpty()
    }

    private fun seedOf(element: JsonElement): Seed? = try {
        Json.decodeFromJsonElement(SeedEntry.serializer(), element).toSeed()
    } catch (_: SerializationException) {
        null
    }

    private const val SEEDS = "seeds"

    @Serializable
    private class SeedEntry(
        val genre: String,
        val tone: String,
        val template: String,
        val counterpart: String,
        val shelf: ShelfEntry,
        val blanks: List<BlankEntry>,
    ) {
        fun toSeed(): Seed? = Seed.of(
            genre,
            tone,
            template,
            counterpart,
            blanks.map {
                Triple(it.key, it.kind.kind, it.fills)
            },
            shelf.register,
        )
    }

    @Serializable
    private enum class ShelfEntry(val register: Register) {
        @SerialName("everyday")
        Everyday(Register.Everyday),

        @SerialName("afterDark")
        AfterDark(Register.AfterDark),

        @SerialName("dark")
        Dark(Register.Dark),

        @SerialName("fantastical")
        Fantastical(Register.Fantastical),

        @SerialName("otherWorlds")
        OtherWorlds(Register.OtherWorlds),

        @SerialName("historical")
        Historical(Register.Historical),
    }

    @Serializable
    private class BlankEntry(val key: String, val kind: KindEntry, val fills: List<String>)

    @Serializable
    private enum class KindEntry(val kind: Seed.Kind) {
        @SerialName("person")
        Person(Seed.Kind.Person),

        @SerialName("thing")
        Thing(Seed.Kind.Thing),

        @SerialName("act")
        Act(Seed.Kind.Act),

        @SerialName("place")
        Place(Seed.Kind.Place),
    }
}
