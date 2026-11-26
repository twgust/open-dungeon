package dev.opendungeon.domain.story

import dev.opendungeon.core.model.ModelId

/** One audition of the opening turn, written by one model. */
public data class Take(val id: TakeId, val model: ModelId, val text: String, val state: State) {
    public enum class State {
        Writing,
        Written,

        /** The writing stopped early: the app died, the player cancelled, or the provider failed. */
        Interrupted,
    }
}
