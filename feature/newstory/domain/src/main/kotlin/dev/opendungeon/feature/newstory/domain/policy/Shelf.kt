package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Register
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.Option
import dev.opendungeon.feature.newstory.domain.OptionGroup
import dev.opendungeon.feature.newstory.domain.vocabulary.VocabularyList
import dev.opendungeon.feature.newstory.domain.vocabulary.WizardVocabulary
import dev.opendungeon.feature.newstory.domain.vocabulary.WordGroup

/**
 * One list's groups as the story so far calls for them. The groups in the registers of the story's
 * genres are open and come first, in the order they are written; the others are folded after them. A
 * story whose genres name no register, such as one in the player's own words, opens every group that is
 * not [opt-in][Register.optIn].
 */
internal class Shelf private constructor(private val groups: List<WordGroup>, private val open: Set<WordGroup>) {

    /** What the die rolls from: the open groups' words, never a folded group's. */
    val rollable: List<String> get() = groups.filter { it in open }.flatMap(WordGroup::words)

    /** Every word on the shelf, open or not, which is what the player may tap. */
    val words: List<String> get() = groups.flatMap(WordGroup::words)

    /**
     * The groups as the step shows them, with [picked] marked. The player's own words lead in a group of
     * their own, and a folded group holding an offered pick opens after the called-for ones, so a pick is
     * never out of sight.
     */
    fun options(picked: List<Phrase>): List<OptionGroup> {
        val pickedWords = picked.filterIsInstance<Phrase.Offered>().mapTo(HashSet(), Phrase::text)
        val own = picked.filterIsInstance<Phrase.Own>().map {
            Option(Choice.Word(it.text), it.text, picked = true, own = true)
        }
        val shown = groups.map { group ->
            group to OptionGroup(
                label = group.label,
                options = group.words.map { Option(Choice.Word(it), it, picked = it in pickedWords) },
                open = group in open || group.words.any(pickedWords::contains),
            )
        }
        val (called, rest) = shown.partition { (group, _) -> group in open }
        val (opened, folded) = rest.partition { (_, shown) -> shown.open }
        val lead = own.takeIf { it.isNotEmpty() }?.let { OptionGroup(label = null, options = it, open = true) }
        return listOfNotNull(lead) + (called + opened + folded).map { (_, shown) -> shown }
    }

    companion object {
        /** The shelf for [list] in a story of [genres]. */
        fun of(vocabulary: WizardVocabulary, list: VocabularyList, genres: List<Phrase>): Shelf {
            val registers = vocabulary.registersOf(genres)
            val groups = vocabulary.groups(list)
            val called = groups.filter { group -> group.registers.any(registers::contains) }
            val open = called.ifEmpty { groups.filterNot { group -> group.registers.all(Register::optIn) } }
            return Shelf(groups, open.toSet())
        }

        /** The genre shelf: every group open, since the genre is what decides the rest. */
        fun ofGenres(vocabulary: WizardVocabulary): Shelf {
            val groups = vocabulary.groups(VocabularyList.Genres)
            return Shelf(groups, groups.toSet())
        }
    }
}

/**
 * The registers of [genres]: those of every group an offered genre is in. A genre in the player's own
 * words matches an offered one only when it is the same words, so it usually names none.
 */
internal fun WizardVocabulary.registersOf(genres: List<Phrase>): Set<Register> {
    val texts = genres.mapTo(HashSet()) { it.text.lowercase() }
    return groups(VocabularyList.Genres)
        .filter { group -> group.words.any { it.lowercase() in texts } }
        .flatMapTo(HashSet(), WordGroup::registers)
}
