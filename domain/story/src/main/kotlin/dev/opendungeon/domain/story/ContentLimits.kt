package dev.opendungeon.domain.story

/** How far the story may go. Every prompt that writes story text carries these. */
public data class ContentLimits(
    val desire: Desire = Desire.Close,
    val violence: Violence = Violence.Felt,
    /** The player's own limits, passed to the model in their words, e.g. "no harm to animals". */
    val note: String? = null,
) {
    public enum class Desire { Unspoken, Close, AllTheWay }

    public enum class Violence { Offstage, Felt, Shown }
}
