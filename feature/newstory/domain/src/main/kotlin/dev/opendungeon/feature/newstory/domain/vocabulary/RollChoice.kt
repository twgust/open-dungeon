package dev.opendungeon.feature.newstory.domain.vocabulary

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.random.Random

/** The die for fixed words. Which words it rolls from is the step's to decide. */
public interface RollChoice {
    /**
     * Picks [count] different words from [words]. Words in [avoiding] are only picked when there are too
     * few others, so a reroll always changes what is on screen when it can.
     */
    public operator fun invoke(
        words: List<String>,
        count: Int = 1,
        avoiding: Collection<String> = emptyList(),
    ): List<String>
}

@ContributesBinding(AppScope::class)
public class DefaultRollChoice internal constructor(private val random: Random) : RollChoice {

    @Inject
    public constructor() : this(Random.Default)

    override fun invoke(words: List<String>, count: Int, avoiding: Collection<String>): List<String> {
        val (fresh, seen) = words.distinct().partition { it !in avoiding }
        return (fresh.shuffled(random) + seen.shuffled(random)).take(count)
    }
}
