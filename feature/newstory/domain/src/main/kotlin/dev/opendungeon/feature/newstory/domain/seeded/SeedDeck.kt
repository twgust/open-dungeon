package dev.opendungeon.feature.newstory.domain.seeded

import dev.opendungeon.domain.drafting.Seed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The seeds that come with the app: instant, free, and dealt before a model writes any. */
public interface SeedDeck {
    public val seeds: List<Seed>
}

/**
 * Reads the seeds bundled with this module. They are data, not code, so they can be edited without
 * touching a rule; a seed in the file that is not whole fails loudly, since only an edit can break it.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BundledSeedDeck : SeedDeck {

    override val seeds: List<Seed> by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream(RESOURCE)) { "Missing $RESOURCE" }
            .bufferedReader()
            .use { it.readText() }
        Json.decodeFromString<SeedFile>(text).seeds.map { entry ->
            requireNotNull(entry.toSeed()) { "Bundled seed is not whole: ${entry.template}" }
        }
    }

    @Serializable
    private class SeedFile(val seeds: List<SeedEntry>)

    @Serializable
    private class SeedEntry(
        val genre: String,
        val tone: String,
        val template: String,
        val counterpart: String,
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
        )
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

    private companion object {
        const val RESOURCE = "/dev/opendungeon/feature/newstory/domain/seeds.json"
    }
}
