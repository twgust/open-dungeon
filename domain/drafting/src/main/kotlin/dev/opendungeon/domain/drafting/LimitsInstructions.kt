package dev.opendungeon.domain.drafting

import dev.opendungeon.domain.story.ContentLimits

/**
 * What each content limit tells the model, in one place to read and edit. Every prompt that writes story
 * text carries these. The player's own note goes in verbatim, after the dials, so it can narrow them.
 *
 * A change here changes every prompt's golden file, so it shows up in review.
 */
internal object LimitsInstructions {

    fun of(limits: ContentLimits): List<String> = listOfNotNull(
        desire(limits.desire),
        violence(limits.violence),
        ADULTS,
        limits.note?.let { "The player also asks: $it" },
    )

    private fun desire(level: ContentLimits.Desire): String = when (level) {
        ContentLimits.Desire.Unspoken ->
            "Desire stays unspoken: looks, tension and longing, nothing physical beyond a touch."
        ContentLimits.Desire.Close ->
            "Desire can go as far as a kiss and a closing door. Anything further happens off the page."
        ContentLimits.Desire.AllTheWay ->
            "Desire can be written all the way, explicitly, when the story leads there."
    }

    private fun violence(level: ContentLimits.Violence): String = when (level) {
        ContentLimits.Violence.Offstage ->
            "Violence happens offstage. Show only what it leaves behind."
        ContentLimits.Violence.Felt ->
            "Violence can happen on the page, felt through its weight and cost, without lingering on wounds."
        ContentLimits.Violence.Shown ->
            "Violence can be shown directly and in detail when the story calls for it."
    }

    private const val ADULTS = "Everyone involved in desire is an adult."
}
