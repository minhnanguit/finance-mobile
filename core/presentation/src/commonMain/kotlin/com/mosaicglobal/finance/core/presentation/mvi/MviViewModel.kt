package com.mosaicglobal.finance.core.presentation.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Base class of every screen ViewModel.
 *
 * - [State]  immutable `data class`, single source of truth for the screen, exposed as [StateFlow].
 * - [Intent] user/system events, handled in [onIntent].
 * - [Effect] one-shot side effects (navigation, snackbars) delivered exactly once via [effects].
 */
abstract class MviViewModel<State : Any, Intent : Any, Effect : Any>(
    initialState: State,
) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<State> = mutableState.asStateFlow()

    // Explicit capacity: Channel(BUFFERED, DROP_OLDEST) would silently collapse to a 1-slot conflated channel.
    private val effectChannel = Channel<Effect>(capacity = EFFECT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    protected val currentState: State
        get() = mutableState.value

    abstract fun onIntent(intent: Intent)

    protected fun setState(reduce: State.() -> State) {
        mutableState.update(reduce)
    }

    protected fun sendEffect(effect: Effect) {
        effectChannel.trySend(effect)
    }

    override fun onCleared() {
        effectChannel.close()
        super.onCleared()
    }

    private companion object {
        const val EFFECT_BUFFER = 64
    }
}
