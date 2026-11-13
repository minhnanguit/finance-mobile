@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.auth.presentation.signedout

import app.cash.turbine.test
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.SignOutNotice
import com.uit.finance.feature.auth.domain.usecase.AcknowledgeSignOutNoticeUseCase
import com.uit.finance.feature.auth.domain.usecase.CheckLocalDataOnDeviceUseCase
import com.uit.finance.feature.auth.domain.usecase.CheckOtherAccountsDataUseCase
import com.uit.finance.feature.auth.domain.usecase.ObserveSignOutNoticeUseCase
import com.uit.finance.feature.auth.domain.usecase.SignInUseCase
import com.uit.finance.feature.auth.domain.usecase.WipeLocalDataUseCase
import com.uit.finance.feature.auth.domain.usecase.WipeOtherAccountsDataUseCase
import com.uit.finance.feature.auth.testing.FakeAuthRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class SignedOutViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()
    private val signedIn = AppResult.Success(SignInResult.SignedIn(Session(null, Instant.fromEpochSeconds(1))))

    private fun viewModel() = SignedOutViewModel(
        SignInUseCase(repository),
        CheckOtherAccountsDataUseCase(repository),
        WipeOtherAccountsDataUseCase(repository),
        ObserveSignOutNoticeUseCase(repository),
        AcknowledgeSignOutNoticeUseCase(repository),
        CheckLocalDataOnDeviceUseCase(repository),
        WipeLocalDataUseCase(repository),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun successfulSignInNavigatesHome() = runTest(dispatcher) {
        repository.signInResult = signedIn
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SignedOutIntent.SignInClicked)
            assertEquals(SignedOutEffect.NavigateToHome, awaitItem())
        }
        assertEquals(SignedOutState(), vm.state.value)
        assertEquals(listOf(SignInMode.SignIn), repository.signInCalls)
    }

    @Test
    fun signUpOpensTheRegistrationMode() = runTest(dispatcher) {
        repository.signInResult = signedIn
        val vm = viewModel()

        vm.onIntent(SignedOutIntent.SignUpClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(SignInMode.SignUp), repository.signInCalls)
    }

    @Test
    fun showsWhichButtonIsWaitingForTheBrowser() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.signInGate = gate
        repository.signInResult = signedIn
        val vm = viewModel()

        vm.state.test {
            assertEquals(SignedOutState(), awaitItem())
            vm.onIntent(SignedOutIntent.SignUpClicked)
            assertEquals(SignedOutState(inProgress = SignInMode.SignUp), awaitItem())
            gate.complete(Unit)
            assertEquals(SignedOutState(), awaitItem())
        }
    }

    @Test
    fun tappingAgainWhileWaitingDoesNotOpenASecondBrowser() = runTest(dispatcher) {
        repository.signInGate = CompletableDeferred()
        val vm = viewModel()

        vm.onIntent(SignedOutIntent.SignInClicked)
        vm.onIntent(SignedOutIntent.SignInClicked)
        vm.onIntent(SignedOutIntent.SignUpClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(SignInMode.SignIn), repository.signInCalls)
    }

    @Test
    fun cancellingShowsNoErrorAndStaysOnTheScreen() = runTest(dispatcher) {
        repository.signInResult = AppResult.Success(SignInResult.Cancelled)
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SignedOutIntent.SignInClicked)
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(SignedOutState(), vm.state.value)
    }

    @Test
    fun anIncompleteSignInAsksTheUserToRetry() = runTest(dispatcher) {
        repository.signInResult = AppResult.Failure(AppError.Unauthorized)
        val vm = viewModel()

        vm.onIntent(SignedOutIntent.SignInClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Đăng nhập chưa hoàn tất. Vui lòng thử lại.", vm.state.value.error?.resolve())
        assertNull(vm.state.value.inProgress)
    }

    @Test
    fun dismissingTheErrorClearsIt() = runTest(dispatcher) {
        repository.signInResult = AppResult.Failure(AppError.Network("offline"))
        val vm = viewModel()
        vm.onIntent(SignedOutIntent.SignInClicked)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SignedOutIntent.ErrorDismissed)

        assertNull(vm.state.value.error)
    }

    @Test
    fun anotherAccountsDataOnTheDeviceIsOfferedForDeletionBeforeEnteringTheApp() = runTest(dispatcher) {
        repository.signInResult = signedIn
        repository.otherAccountsResult = AppResult.Success(1)
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SignedOutIntent.SignInClicked)
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.state.value.otherAccountsPrompt)
            expectNoEvents()

            vm.onIntent(SignedOutIntent.WipeOtherAccounts)
            assertEquals(SignedOutEffect.NavigateToHome, awaitItem())
        }
        assertEquals(1, repository.wipeOtherCalls)
        assertFalse(vm.state.value.otherAccountsPrompt)
    }

    @Test
    fun keepingTheOtherAccountsDataEntersWithoutDeleting() = runTest(dispatcher) {
        repository.signInResult = signedIn
        repository.otherAccountsResult = AppResult.Success(2)
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SignedOutIntent.SignInClicked)
            dispatcher.scheduler.advanceUntilIdle()
            vm.onIntent(SignedOutIntent.KeepOtherAccounts)
            assertEquals(SignedOutEffect.NavigateToHome, awaitItem())
        }
        assertEquals(0, repository.wipeOtherCalls)
    }

    @Test
    fun failingToCheckOtherAccountsDoesNotBlockSignIn() = runTest(dispatcher) {
        repository.signInResult = signedIn
        repository.otherAccountsResult = AppResult.Failure(AppError.Network("offline"))
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SignedOutIntent.SignInClicked)
            assertEquals(SignedOutEffect.NavigateToHome, awaitItem())
        }
    }

    @Test
    fun accountDeletedNoticeIsShownOnceAndCanBeDismissed() = runTest(dispatcher) {
        repository.notice.value = SignOutNotice.AccountDeleted
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.notice != null, "phải báo tài khoản đã bị xoá")

        vm.onIntent(SignedOutIntent.NoticeDismissed)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.notice)
        assertEquals(1, repository.acknowledgeCalls)
    }

    @Test
    fun leftoverDataWithoutASessionCanBeWipedAfterConfirming() = runTest(dispatcher) {
        repository.localDataWithoutSession = true
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.localDataOnDevice, "máy còn sổ mà không có phiên: cho xoá")

        vm.onIntent(SignedOutIntent.WipeLocalDataClicked)
        assertTrue(vm.state.value.wipeLocalDataPrompt, "phải hỏi lại trước khi xoá")
        vm.onIntent(SignedOutIntent.WipeLocalDataDismissed)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repository.wipeLocalCalls, "đóng hộp thoại thì không xoá")

        vm.onIntent(SignedOutIntent.WipeLocalDataClicked)
        vm.onIntent(SignedOutIntent.WipeLocalDataConfirmed)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.wipeLocalCalls)
        assertFalse(vm.state.value.localDataOnDevice)
        assertTrue(vm.state.value.localDataWiped)
        vm.onIntent(SignedOutIntent.LocalDataWipedDismissed)
        assertFalse(vm.state.value.localDataWiped)
    }

    @Test
    fun noLeftoverDataMeansNoWipeOption() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.localDataOnDevice)
    }

    @Test
    fun signingInAgainClearsTheOldNotice() = runTest(dispatcher) {
        repository.notice.value = SignOutNotice.AccountDeleted
        repository.signInResult = AppResult.Success(SignInResult.Cancelled)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SignedOutIntent.SignUpClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.notice)
        assertEquals(1, repository.acknowledgeCalls)
    }
}
