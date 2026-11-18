@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.settings.presentation

import app.cash.turbine.test
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.model.AccountProfile
import com.uit.finance.feature.settings.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.settings.domain.usecase.DeleteAccountUseCase
import com.uit.finance.feature.settings.domain.usecase.ObserveAccountProfileUseCase
import com.uit.finance.feature.settings.presentation.deleteaccount.DeleteAccountEffect
import com.uit.finance.feature.settings.presentation.deleteaccount.DeleteAccountIntent
import com.uit.finance.feature.settings.presentation.deleteaccount.DeleteAccountViewModel
import com.uit.finance.feature.settings.testing.FakeAccountRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class DeleteAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAccountRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DeleteAccountViewModel(
        observeProfile = ObserveAccountProfileUseCase(repository),
        countUnsentChanges = CountUnsentChangesUseCase(repository),
        deleteAccount = DeleteAccountUseCase(repository),
    )

    @Test
    fun `hiện email và số thay đổi sẽ mất - nút xoá chỉ sáng khi đã gõ XOÁ`() = runTest(dispatcher) {
        repository.profile.value = AccountProfile("Ann", "ann@example.com", null)
        repository.unsent = 4
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("ann@example.com", vm.state.value.email)
        assertEquals(4L, vm.state.value.unsentChanges)
        assertFalse(vm.state.value.canSubmit)

        vm.onIntent(DeleteAccountIntent.Submit)
        advanceUntilIdle()
        assertEquals(0, repository.deleteCalls, "chưa gõ xác nhận thì không làm gì")

        vm.onIntent(DeleteAccountIntent.ConfirmationChanged("xoá"))
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `xoá xong thì về màn đăng nhập - đang chờ trình duyệt thì không bấm lần hai và không quay lại`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.deletionGate = gate
        val vm = viewModel()
        vm.onIntent(DeleteAccountIntent.ConfirmationChanged("XOÁ"))

        vm.effects.test {
            vm.onIntent(DeleteAccountIntent.Submit)
            advanceUntilIdle()
            assertTrue(vm.state.value.isDeleting)
            vm.onIntent(DeleteAccountIntent.Submit)
            vm.onIntent(DeleteAccountIntent.BackClicked)
            advanceUntilIdle()
            assertEquals(1, repository.deleteCalls)

            gate.complete(Unit)
            assertEquals(DeleteAccountEffect.Deleted, awaitItem())
        }
    }

    @Test
    fun `đóng trình duyệt thì quay lại như cũ - không báo lỗi`() = runTest(dispatcher) {
        repository.deletionResult = AppResult.Success(AccountDeletion.Cancelled)
        val vm = viewModel()
        vm.onIntent(DeleteAccountIntent.ConfirmationChanged("XOA"))

        vm.onIntent(DeleteAccountIntent.Submit)
        advanceUntilIdle()

        assertFalse(vm.state.value.isDeleting)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `đăng nhập nhầm tài khoản hoặc mất mạng thì giải thích và cho thử lại`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(DeleteAccountIntent.ConfirmationChanged("XOÁ"))

        repository.deletionResult = AppResult.Success(AccountDeletion.DifferentAccount)
        vm.onIntent(DeleteAccountIntent.Submit)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.canSubmit)

        repository.deletionResult = AppResult.Failure(AppError.Network("offline"))
        vm.onIntent(DeleteAccountIntent.Submit)
        advanceUntilIdle()
        assertEquals(
            "Cần kết nối mạng để xoá tài khoản. Dữ liệu chưa bị xoá.",
            vm.state.value.error?.resolve(),
        )
    }

    @Test
    fun `chưa rõ server đã xoá chưa thì không khẳng định gì - cho thử lại`() = runTest(dispatcher) {
        repository.deletionResult = AppResult.Success(AccountDeletion.Unconfirmed)
        val vm = viewModel()
        vm.onIntent(DeleteAccountIntent.ConfirmationChanged("XOÁ"))

        vm.onIntent(DeleteAccountIntent.Submit)
        advanceUntilIdle()

        val message = assertNotNull(vm.state.value.error?.resolve())
        assertTrue("chưa rõ" in message)
        assertFalse("chưa bị xoá" in message)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `lỗi máy chủ không lộ title kỹ thuật - luôn nói rõ dữ liệu chưa bị xoá`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(DeleteAccountIntent.ConfirmationChanged("XOÁ"))

        repository.deletionResult = AppResult.Failure(AppError.Api(status = 406, code = null, title = "Not Acceptable", detail = null))
        vm.onIntent(DeleteAccountIntent.Submit)
        advanceUntilIdle()
        assertEquals(
            "Máy chủ chưa xoá được tài khoản. Dữ liệu chưa bị xoá, hãy thử lại sau.",
            vm.state.value.error?.resolve(),
        )
        assertTrue(vm.state.value.canSubmit)

        repository.deletionResult = AppResult.Failure(AppError.Api(status = 429, code = "rate_limited", title = "Too Many Requests", detail = null))
        vm.onIntent(DeleteAccountIntent.Submit)
        advanceUntilIdle()
        assertEquals(
            "Bạn đã thử nhiều lần. Đợi một phút rồi thử lại. Dữ liệu chưa bị xoá.",
            vm.state.value.error?.resolve(),
        )
    }
}
