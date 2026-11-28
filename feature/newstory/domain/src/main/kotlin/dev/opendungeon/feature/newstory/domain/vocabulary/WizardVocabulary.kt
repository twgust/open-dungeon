package dev.opendungeon.feature.newstory.domain.vocabulary

import dev.opendungeon.domain.story.Register
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The fixed words the wizard offers as pills and rolls with its dice, in labelled groups. */
public interface WizardVocabulary {
    /** The groups of [list], in the order they are written. */
    public fun groups(list: VocabularyList): List<WordGroup>

    /** Every word of [list], group after group. */
    public operator fun get(list: VocabularyList): List<String> = groups(list).flatMap(WordGroup::words)
}

public enum class VocabularyList { Genres, Twists, Roles, Tones, Settings, Wants, Flaws, GoodAt }

/** Words that belong together under one [label], offered first to stories in any of its [registers]. */
public data class WordGroup(val label: String, val registers: Set<Register>, val words: List<String>) {
    init {
        require(label.isNotBlank()) { "A group needs a label" }
        require(registers.isNotEmpty()) { "$label belongs to no register" }
        require(words.isNotEmpty()) { "$label has no words" }
    }
}

/**
 * Reads the vocabulary bundled with this module. The words are data, not code, so they can be edited
 * without touching a rule.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BundledWizardVocabulary : WizardVocabulary {

    private val lists: Map<VocabularyList, List<WordGroup>> by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream(RESOURCE)) { "Missing $RESOURCE" }
            .bufferedReader()
            .use { it.readText() }
        Json.decodeFromString<VocabularyFile>(text).toMap()
    }

    override fun groups(list: VocabularyList): List<WordGroup> = lists.getValue(list)

    @Serializable
    private class VocabularyFile(
        val genres: List<GroupEntry>,
        val twists: List<GroupEntry>,
        val roles: List<GroupEntry>,
        val tones: List<GroupEntry>,
        val settings: List<GroupEntry>,
        val wants: List<GroupEntry>,
        val flaws: List<GroupEntry>,
        val goodAt: List<GroupEntry>,
    ) {
        fun toMap(): Map<VocabularyList, List<WordGroup>> = mapOf(
            VocabularyList.Genres to genres,
            VocabularyList.Twists to twists,
            VocabularyList.Roles to roles,
            VocabularyList.Tones to tones,
            VocabularyList.Settings to settings,
            VocabularyList.Wants to wants,
            VocabularyList.Flaws to flaws,
            VocabularyList.GoodAt to goodAt,
        ).mapValues { (_, groups) -> groups.map(GroupEntry::toGroup) }
    }

    @Serializable
    private class GroupEntry(val label: String, val registers: List<RegisterEntry>, val words: List<String>) {
        fun toGroup(): WordGroup = WordGroup(label, registers.mapTo(LinkedHashSet(), RegisterEntry::register), words)
    }

    @Serializable
    private enum class RegisterEntry(val register: Register) {
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

    private companion object {
        const val RESOURCE = "/dev/opendungeon/feature/newstory/domain/vocabulary.json"
    }
}
