@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.auth.presentation.profile

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.UserProfile
import com.uit.finance.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.uit.finance.feature.auth.domain.usecase.LogoutUseCase
import com.uit.finance.feature.auth.testing.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAuthRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun failedLogoutKeepsAnActionToRetryLogout() = runTest(dispatcher) {
        repository.currentUserResult = AppResult.Success(
            UserProfile("user-1", "user@example.com", "Người dùng", Instant.fromEpochSeconds(1)),
        )
        repository.logoutResult = AppResult.Failure(AppError.Network("offline"))
        val viewModel = ProfileViewModel(GetCurrentUserUseCase(repository), LogoutUseCase(repository))
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
