@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.uit.finance.feature.settings.presentation

import app.cash.turbine.test
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.settings.domain.model.AccountProfile
import com.uit.finance.feature.settings.domain.model.PasswordChange
import com.uit.finance.feature.settings.domain.model.SettingsAppInfo
import com.uit.finance.feature.settings.domain.usecase.ChangePasswordUseCase
import com.uit.finance.feature.settings.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.settings.domain.usecase.LogoutUseCase
import com.uit.finance.feature.settings.domain.usecase.ObserveAccountProfileUseCase
import com.uit.finance.feature.settings.presentation.settings.SettingsEffect
import com.uit.finance.feature.settings.presentation.settings.SettingsIntent
import com.uit.finance.feature.settings.presentation.settings.SettingsViewModel
import com.uit.finance.feature.settings.testing.FakeAccountRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccountRepository()
    private val appInfo = SettingsAppInfo("1.4.0", "https://finance.test/privacy")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(
        observeProfile = ObserveAccountProfileUseCase(accounts),
        appInfo = appInfo,
        changePassword = ChangePasswordUseCase(accounts),
        countUnsentChanges = CountUnsentChangesUseCase(accounts),
        logout = LogoutUseCase(accounts),
    )

    @Test
    fun `hiện hồ sơ và phiên bản`() = runTest(dispatcher) {
        accounts.profile.value = AccountProfile("Ann", "ann@example.com", Instant.parse("2026-10-15T08:00:00Z"))
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Ann", state.profile?.displayName)
        assertEquals("Thành viên từ 10/2026", state.profile?.memberSince)
        assertEquals("1.4.0", state.versionName)
    }

    @Test
    fun `đổi mật khẩu xong thì báo - huỷ thì im lặng`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.ChangePasswordClicked)
        advanceUntilIdle()
        assertEquals("Đã đổi mật khẩu", vm.state.value.message?.resolve())
        assertFalse(vm.state.value.isChangingPassword)

        vm.onIntent(SettingsIntent.MessageShown)
        accounts.passwordChangeResult = AppResult.Success(PasswordChange.Cancelled)
        vm.onIntent(SettingsIntent.ChangePasswordClicked)
        advanceUntilIdle()
        assertNull(vm.state.value.message)
        assertEquals(2, accounts.passwordChangeCalls)
    }

    @Test
    fun `đổi nhầm tài khoản khác hoặc lỗi mạng thì báo`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        accounts.passwordChangeResult = AppResult.Success(PasswordChange.DifferentAccount)
        vm.onIntent(SettingsIntent.ChangePasswordClicked)
        advanceUntilIdle()
        assertTrue(vm.state.value.message?.resolve().orEmpty().contains("tài khoản khác"))

        vm.onIntent(SettingsIntent.MessageShown)
        accounts.passwordChangeResult = AppResult.Failure(AppError.Network("offline"))
        vm.onIntent(SettingsIntent.ChangePasswordClicked)
        advanceUntilIdle()
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.isChangingPassword)
    }

    @Test
    fun `đang mở form đổi mật khẩu thì bấm thêm không mở lần hai`() = runTest(dispatcher) {
        val browsing = CompletableDeferred<Unit>()
        accounts.passwordChangeGate = browsing
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.ChangePasswordClicked)
        advanceUntilIdle()
        assertTrue(vm.state.value.isChangingPassword)
        vm.onIntent(SettingsIntent.ChangePasswordClicked)
        browsing.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, accounts.passwordChangeCalls)
        assertFalse(vm.state.value.isChangingPassword)
    }

    @Test
    fun `còn thay đổi chưa gửi thì hỏi trước khi đăng xuất`() = runTest(dispatcher) {
        accounts.unsent = 2
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(SettingsIntent.LogoutClicked)
            advanceUntilIdle()
            assertEquals(2L, vm.state.value.unsentChangesWarning)
            assertEquals(0, accounts.logoutCalls)

            vm.onIntent(SettingsIntent.LogoutConfirmed)
            assertEquals(SettingsEffect.LoggedOut, awaitItem())
        }
        assertEquals(1, accounts.logoutCalls)
    }

    @Test
    fun `mở chính sách quyền riêng tư trong trình duyệt và mở màn xoá tài khoản`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SettingsIntent.PrivacyPolicyClicked)
            assertEquals(SettingsEffect.OpenUrl("https://finance.test/privacy"), awaitItem())
            vm.onIntent(SettingsIntent.DeleteAccountClicked)
            assertEquals(SettingsEffect.OpenDeleteAccount, awaitItem())
        }
    }

    @Test
    fun `không còn thay đổi chưa gửi thì đăng xuất luôn - không hỏi`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(SettingsIntent.LogoutClicked)
            assertEquals(SettingsEffect.LoggedOut, awaitItem())
        }
        assertNull(vm.state.value.unsentChangesWarning)
        assertEquals(1, accounts.logoutCalls)
    }

    @Test
    fun `đóng cảnh báo thay đổi chưa gửi thì ở lại - không đăng xuất`() = runTest(dispatcher) {
        accounts.unsent = 3
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.LogoutClicked)
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.LogoutWarningDismissed)
        advanceUntilIdle()

        assertNull(vm.state.value.unsentChangesWarning)
        assertEquals(0, accounts.logoutCalls)
    }

    @Test
    fun `đăng xuất lỗi thì báo và cho bấm lại`() = runTest(dispatcher) {
        accounts.logoutResult = AppResult.Failure(AppError.Storage("disk"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.LogoutClicked)
        advanceUntilIdle()

        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.isLoggingOut)
        vm.onIntent(SettingsIntent.LogoutClicked)
        advanceUntilIdle()
        assertEquals(2, accounts.logoutCalls)
    }
}
