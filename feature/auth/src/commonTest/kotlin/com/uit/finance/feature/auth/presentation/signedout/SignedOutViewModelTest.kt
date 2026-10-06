@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.auth.presentation.signedout

import app.cash.turbine.test
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.usecase.SignInUseCase
import com.uit.finance.feature.auth.testing.FakeAuthRepository
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SignedOutViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()
    private val signedIn = AppResult.Success(SignInResult.SignedIn(Session(null, Instant.fromEpochSeconds(1))))

    private fun viewModel() = SignedOutViewModel(SignInUseCase(repository))

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
}
