@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.auth.presentation.profile

import app.cash.turbine.test
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.UserProfile
import com.uit.finance.feature.auth.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.uit.finance.feature.auth.domain.usecase.LogoutUseCase
import com.uit.finance.feature.auth.testing.FakeAuthRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ProfileViewModel(GetCurrentUserUseCase(repository), LogoutUseCase(repository), CountUnsentChangesUseCase(repository))

    @Test
    fun logoutWithUnsentChangesAsksFirstAndDoesNotLogOut() = runTest(dispatcher) {
        repository.unsentChanges = 3
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.Logout)
        advanceUntilIdle()

        assertEquals(3L, viewModel.state.value.unsentChangesWarning)
        assertEquals(0, repository.logoutCalls)

        viewModel.onIntent(ProfileIntent.DismissLogoutWarning)
        advanceUntilIdle()

        assertNull(viewModel.state.value.unsentChangesWarning)
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun confirmingTheWarningLogsOut() = runTest(dispatcher) {
        repository.unsentChanges = 3
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.Logout)
            advanceUntilIdle()
            viewModel.onIntent(ProfileIntent.ConfirmLogout)
            assertEquals(ProfileEffect.LoggedOut, awaitItem())
        }
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun logoutWithNothingUnsentGoesStraightThrough() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.Logout)
        advanceUntilIdle()

        assertNull(viewModel.state.value.unsentChangesWarning)
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun failedLogoutKeepsAnActionToRetryLogout() = runTest(dispatcher) {
        repository.currentUserResult = AppResult.Success(
            UserProfile("user-1", "user@example.com", "Người dùng", Instant.fromEpochSeconds(1)),
        )
        repository.logoutResult = AppResult.Failure(AppError.Network("offline"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(ProfileIntent.Logout)
        advanceUntilIdle()

        assertEquals(ProfileErrorAction.RetryLogout, viewModel.state.value.errorAction)
        assertNotNull(viewModel.state.value.error)
        assertEquals(1, repository.logoutCalls)

        viewModel.onIntent(ProfileIntent.Logout)
        advanceUntilIdle()

        assertEquals(2, repository.logoutCalls)
    }
}
