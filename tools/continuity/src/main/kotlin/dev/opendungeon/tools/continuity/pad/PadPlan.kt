package dev.opendungeon.tools.continuity.pad

/** Where interludes go when a scenario is stretched. */
internal object PadPlan {

    /**
     * How many interludes go into each of the gaps between [keyTurns] hand-written turns so the story
     * reaches [targetTurns]: as evenly as possible, the extra ones in the later gaps, so the earliest facts
     * end up no closer than the rest.
     */
    fun spread(keyTurns: Int, targetTurns: Int): List<Int> {
        require(keyTurns >= 2) { "Padding needs two turns to put interludes between, not $keyTurns" }
        require(targetTurns >= keyTurns) { "A scenario of $keyTurns turns cannot be padded down to $targetTurns" }
        val gaps = keyTurns - 1
        val interludes = targetTurns - keyTurns
        val each = interludes / gaps
        val extra = interludes % gaps
        return List(gaps) { gap -> each + if (gap >= gaps - extra) 1 else 0 }
    }
}
