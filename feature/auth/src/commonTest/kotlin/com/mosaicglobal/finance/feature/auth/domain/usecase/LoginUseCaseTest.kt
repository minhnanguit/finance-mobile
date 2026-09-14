@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.feature.auth.domain.usecase

import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.Credentials
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.testing.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class LoginUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = LoginUseCase(repository)

    @Test
    fun rejectsInvalidEmailWithoutCallingRepository() = runTest {
        val result = useCase("not-an-email", "secret")

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Validation>(failure.error)
        assertEquals(listOf("email"), error.fieldErrors.map { it.field })
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun rejectsEmptyPassword() = runTest {
        val result = useCase("user@example.com", "")
        val error = assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error)
        assertEquals(listOf("password"), error.fieldErrors.map { it.field })
    }

    @Test
    fun trimsEmailAndDelegates() = runTest {
        val session = Session(userId = null, accessTokenExpiresAt = Instant.fromEpochSeconds(10))
        repository.loginResult = AppResult.Success(session)

        val result = useCase("  user@example.com ", "hunter22")

        assertEquals(AppResult.Success(session), result)
        assertEquals(listOf(Credentials("user@example.com", "hunter22")), repository.loginCalls)
    }
}
