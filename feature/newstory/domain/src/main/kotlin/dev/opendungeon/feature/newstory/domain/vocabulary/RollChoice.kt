package dev.opendungeon.feature.newstory.domain.vocabulary

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.random.Random

/** The die for fixed words. */
public interface RollChoice {
    /**
     * Picks [count] different words from [list]. Words in [avoiding] are only picked when the list has
     * too few others, so a reroll always changes what is on screen when it can.
     */
    public operator fun invoke(
        list: VocabularyList,
        count: Int = 1,
        avoiding: Collection<String> = emptyList(),
    ): List<String>
}

@ContributesBinding(AppScope::class)
public class DefaultRollChoice internal constructor(
    private val vocabulary: WizardVocabulary,
    private val random: Random,
) : RollChoice {

    @Inject
    public constructor(vocabulary: WizardVocabulary) : this(vocabulary, Random.Default)

    override fun invoke(list: VocabularyList, count: Int, avoiding: Collection<String>): List<String> {
        val (fresh, seen) = vocabulary[list].partition { it !in avoiding }
        return (fresh.shuffled(random) + seen.shuffled(random)).take(count)
    }
}
