@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.auth.presentation.localdata

import app.cash.turbine.test
import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import com.uit.finance.feature.auth.domain.usecase.ObserveLocalDataStatusUseCase
import com.uit.finance.feature.auth.domain.usecase.RetryLocalDataUseCase
import com.uit.finance.feature.auth.testing.FakeAuthRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class LocalDataGateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LocalDataGateViewModel(ObserveLocalDataStatusUseCase(repository), RetryLocalDataUseCase(repository))

    @Test
    fun `mất mạng lần đầu thì thử lại - có mạng thì vào app`() = runTest(dispatcher) {
        repository.localData.value = LocalDataStatus.NeedsNetwork
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LocalDataStatus.NeedsNetwork, vm.state.value.status)

        vm.effects.test {
            vm.onIntent(LocalDataGateIntent.RetryClicked)
            assertTrue(vm.state.value.isRetrying)
            assertEquals(1, repository.retryLocalDataCalls)

            repository.localData.value = LocalDataStatus.Ready
            advanceUntilIdle()
            assertEquals(LocalDataGateEffect.Ready, awaitItem())
        }
    }
}
