package dev.opendungeon.feature.newstory.domain.policy

import dev.opendungeon.domain.story.Phrase

/**
 * The player's words as a phrase. Words that match an offered one, ignoring case, are that offered
 * word: typing "gothic" is picking Gothic. Blank words are no phrase at all.
 */
internal fun phraseFor(text: String, offered: List<String>): Phrase? {
    val words = text.trim().takeIf(String::isNotEmpty) ?: return null
    return offered.firstOrNull { it.equals(words, ignoreCase = true) }?.let(Phrase::Offered) ?: Phrase.Own(words)
}
