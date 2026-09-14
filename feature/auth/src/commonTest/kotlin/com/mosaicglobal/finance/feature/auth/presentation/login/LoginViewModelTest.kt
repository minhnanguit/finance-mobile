@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.mosaicglobal.finance.feature.auth.presentation.login

import app.cash.turbine.test
import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.common.result.FieldError
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.usecase.LoginUseCase
import com.mosaicglobal.finance.feature.auth.testing.FakeAuthRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()

    private fun viewModel() = LoginViewModel(LoginUseCase(repository))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typingUpdatesStateAndClearsErrors() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(LoginIntent.EmailChanged("a@b.co"))
        vm.onIntent(LoginIntent.PasswordChanged("pw"))

        assertEquals(LoginState(email = "a@b.co", password = "pw"), vm.state.value)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun successfulLoginEmitsNavigateToHome() = runTest(dispatcher) {
        repository.loginResult = AppResult.Success(Session(null, Instant.fromEpochSeconds(1)))
        val vm = viewModel()
        vm.onIntent(LoginIntent.EmailChanged("user@example.com"))
        vm.onIntent(LoginIntent.PasswordChanged("hunter22"))

        vm.effects.test {
            vm.onIntent(LoginIntent.Submit)
            assertEquals(LoginEffect.NavigateToHome, awaitItem())
        }
        assertFalse(vm.state.value.isSubmitting)
        assertEquals("", vm.state.value.password, "password must not be kept in state after login")
        assertEquals(1, repository.loginCalls.size)
    }

    @Test
    fun showsSubmittingWhileRequestIsInFlight() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.loginGate = gate
        repository.loginResult = AppResult.Success(Session(null, Instant.fromEpochSeconds(1)))
        val vm = viewModel()
        vm.onIntent(LoginIntent.EmailChanged("user@example.com"))
        vm.onIntent(LoginIntent.PasswordChanged("hunter22"))

        vm.state.test {
            assertFalse(awaitItem().isSubmitting)
            vm.onIntent(LoginIntent.Submit)
            assertTrue(awaitItem().isSubmitting)
            gate.complete(Unit)
            assertFalse(awaitItem().isSubmitting)
        }
    }

    @Test
    fun validationErrorsLandOnTheirFields() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(LoginIntent.EmailChanged("nope"))
        vm.onIntent(LoginIntent.PasswordChanged("x"))

        vm.onIntent(LoginIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.error)
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun unauthorizedBecomesInvalidCredentialsMessage() = runTest(dispatcher) {
        repository.loginResult = AppResult.Failure(AppError.Unauthorized)
        val vm = viewModel()
        vm.onIntent(LoginIntent.EmailChanged("user@example.com"))
        vm.onIntent(LoginIntent.PasswordChanged("wrong-pass"))

        vm.onIntent(LoginIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Email or password is incorrect.", vm.state.value.error?.resolve())
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun serverFieldErrorsAreMappedFromProblemDetails() = runTest(dispatcher) {
        repository.loginResult = AppResult.Failure(
            AppError.Api(status = 400, code = "validation", title = "Bad Request", detail = null,
                fieldErrors = listOf(FieldError("email", "must be a well-formed email address"))),
        )
        val vm = viewModel()
        vm.onIntent(LoginIntent.EmailChanged("user@example.com"))
        vm.onIntent(LoginIntent.PasswordChanged("hunter22"))

        vm.onIntent(LoginIntent.Submit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("must be a well-formed email address", vm.state.value.error?.resolve())
    }

    @Test
    fun registerClickedNavigates() = runTest(dispatcher) {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(LoginIntent.RegisterClicked)
            assertEquals(LoginEffect.NavigateToRegister, awaitItem())
        }
    }
}
