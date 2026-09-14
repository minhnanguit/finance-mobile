package com.mosaicglobal.finance.feature.auth.domain.usecase

import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.testing.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RegisterUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = RegisterUseCase(repository)

    @Test
    fun collectsAllFieldErrorsAtOnce() = runTest {
        val result = useCase(email = "bad", password = "short", displayName = " ")
        val error = assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error)
        assertEquals(setOf("email", "password", "displayName"), error.fieldErrors.map { it.field }.toSet())
        assertEquals(0, repository.registerCalls.size)
    }

    @Test
    fun enforcesPasswordLengthFromContract() = runTest {
        val tooShort = useCase("user@example.com", "1234567", "Nam")
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(tooShort).error)

        val tooLong = useCase("user@example.com", "x".repeat(73), "Nam")
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(tooLong).error)
    }
}
