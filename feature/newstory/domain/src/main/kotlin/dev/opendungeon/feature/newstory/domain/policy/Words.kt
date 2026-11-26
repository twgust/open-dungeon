package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.feature.newstory.domain.Choice
import dev.opendungeon.feature.newstory.domain.Option

/** The player's own picks first, so they stay in view, then every offered word. */
internal fun wordOptions(offered: List<String>, picked: List<Phrase>): List<Option> {
    val own = picked.filterIsInstance<Phrase.Own>().map {
        Option(Choice.Word(it.text), it.text, picked = true, own = true)
    }
    val offeredPicks = picked.filterIsInstance<Phrase.Offered>().mapTo(HashSet(), Phrase::text)
    return own + offered.map { Option(Choice.Word(it), it, picked = it in offeredPicks) }
}

/**
 * The player's words as a phrase. Words that match an offered one, ignoring case, are that offered
 * word: typing "gothic" is picking Gothic. Blank words are no phrase at all.
 */
internal fun phraseFor(text: String, offered: List<String>): Phrase? {
    val words = text.trim().takeIf(String::isNotEmpty) ?: return null
    return offered.firstOrNull { it.equals(words, ignoreCase = true) }?.let(Phrase::Offered) ?: Phrase.Own(words)
}
