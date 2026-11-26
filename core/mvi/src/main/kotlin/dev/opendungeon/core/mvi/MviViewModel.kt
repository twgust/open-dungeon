package dev.opendungeon.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * The single MVI contract every screen follows.
 *
 * - [State] is an immutable snapshot the UI renders. It is the only source of truth for what is on screen.
 * - [Intent] is everything the user (or the system) asks the screen to do. The UI only calls [onIntent].
 * - [Effect] is a one-off event that must not be replayed on recomposition or rotation,
 *   such as navigation or a snackbar. Effects are delivered exactly once to one collector.
 *
 * State changes go through [reduce], which applies a pure `State -> State` function atomically,
 * so concurrent work cannot lose updates. There is deliberately no way to read the current state from
 * a subclass: deciding what is allowed from it is business logic, and that belongs in an interactor.
 */
public abstract class MviViewModel<State : Any, Intent : Any, Effect : Any>(
    initialState: State,
) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)
    public val state: StateFlow<State> = mutableState.asStateFlow()

    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    public val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    /** Entry point for the UI. Implementations decide which intents launch work in `viewModelScope`. */
    public abstract fun onIntent(intent: Intent)

    protected fun reduce(reducer: State.() -> State) {
        mutableState.update(reducer)
    }

    protected fun sendEffect(effect: Effect) {
        effectChannel.trySend(effect)
    }
}
