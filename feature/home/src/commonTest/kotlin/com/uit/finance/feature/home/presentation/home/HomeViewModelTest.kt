@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.home.presentation.home

import app.cash.turbine.test
import com.uit.finance.feature.home.presentation.navigation.HomeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithoutOverviewUntilLedgerDataExists() {
        assertNull(HomeViewModel().state.value.overview)
    }

    @Test
    fun everyTargetIsForwardedAsExactlyOneNavigationEffect() = runTest(dispatcher) {
        val vm = HomeViewModel()

        vm.effects.test {
            HomeTarget.entries.forEach { target ->
                vm.onIntent(HomeIntent.Open(target))
                assertEquals(HomeEffect.Navigate(target), awaitItem())
            }
            expectNoEvents()
        }
    }
}
