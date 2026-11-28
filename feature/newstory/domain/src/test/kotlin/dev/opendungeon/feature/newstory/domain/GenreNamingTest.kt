package dev.opendungeon.feature.newstory.domain

import dev.opendungeon.domain.drafting.BundledNamingGuide
import dev.opendungeon.domain.story.DraftId
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.StoryDraft
import dev.opendungeon.feature.newstory.domain.seeded.BundledSeedDeck
import dev.opendungeon.feature.newstory.domain.vocabulary.BundledWizardVocabulary
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every genre the wizards offer has its own way of naming people, so none falls back to the model's habits. */
class GenreNamingTest {

    private val guide = BundledNamingGuide()

    private fun unnamed(genres: Collection<String>): List<String> = genres.filter { genre ->
        guide.of(StoryDraft(id = DraftId("d"), genres = listOf(Phrase.Offered(genre)))).size < 2
    }

    @Test
    fun `every genre the custom wizard offers has a naming style`() {
        assertEquals(emptyList(), unnamed(BundledWizardVocabulary()[VocabularyList.Genres]))
    }

    @Test
    fun `every genre a seed is written in has a naming style`() {
        assertEquals(emptyList(), unnamed(BundledSeedDeck().seeds.mapTo(sortedSetOf()) { it.genre }))
    }
}
