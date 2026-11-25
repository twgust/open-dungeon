package dev.opendungeon.feature.newstory.domain.vocabulary

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The fixed words the wizard offers as pills and rolls with its dice. */
public interface WizardVocabulary {
    public operator fun get(list: VocabularyList): List<String>
}

public enum class VocabularyList { Genres, Twists, Roles, Tones, Settings, Wants, Flaws, GoodAt }

/**
 * Reads the vocabulary bundled with this module. The words are data, not code, so they can be edited
 * without touching a rule.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BundledWizardVocabulary : WizardVocabulary {

    private val lists: Map<VocabularyList, List<String>> by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream(RESOURCE)) { "Missing $RESOURCE" }
            .bufferedReader()
            .use { it.readText() }
        Json.decodeFromString<VocabularyFile>(text).toMap()
    }

    override fun get(list: VocabularyList): List<String> = lists.getValue(list)

    @Serializable
    private class VocabularyFile(
        val genres: List<String>,
        val twists: List<String>,
        val roles: List<String>,
        val tones: List<String>,
        val settings: List<String>,
        val wants: List<String>,
        val flaws: List<String>,
        val goodAt: List<String>,
    ) {
        fun toMap(): Map<VocabularyList, List<String>> = mapOf(
            VocabularyList.Genres to genres,
            VocabularyList.Twists to twists,
            VocabularyList.Roles to roles,
            VocabularyList.Tones to tones,
            VocabularyList.Settings to settings,
            VocabularyList.Wants to wants,
            VocabularyList.Flaws to flaws,
            VocabularyList.GoodAt to goodAt,
        )
    }

    private companion object {
        const val RESOURCE = "/dev/opendungeon/feature/newstory/domain/vocabulary.json"
    }
}
