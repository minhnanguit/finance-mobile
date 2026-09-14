package com.mosaicglobal.finance.core.presentation.mvi

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MviViewModelTest {

    private data class CounterState(val count: Int = 0)
    private sealed interface CounterIntent {
        data object Increment : CounterIntent
        data object Celebrate : CounterIntent
    }
    private sealed interface CounterEffect {
        data class Toast(val text: String) : CounterEffect
    }

    private class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState()) {
        override fun onIntent(intent: CounterIntent) = when (intent) {
            CounterIntent.Increment -> setState { copy(count = count + 1) }
            CounterIntent.Celebrate -> sendEffect(CounterEffect.Toast("count=${currentState.count}"))
        }
    }

    @Test
    fun stateIsReducedImmutably() = runTest {
        val vm = CounterViewModel()
        val initial = vm.state.value
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Increment)
        assertEquals(CounterState(0), initial)
        assertEquals(CounterState(2), vm.state.value)
    }

    @Test
    fun effectsAreDeliveredOnce() = runTest {
        val vm = CounterViewModel()
        vm.onIntent(CounterIntent.Increment)
        vm.onIntent(CounterIntent.Celebrate)
        vm.effects.test {
            assertEquals(CounterEffect.Toast("count=1"), awaitItem())
            expectNoEvents()
        }
    }
}
