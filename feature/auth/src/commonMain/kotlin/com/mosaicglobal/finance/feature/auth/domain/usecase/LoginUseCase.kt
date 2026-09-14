package com.mosaicglobal.finance.feature.auth.domain.usecase

import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.Credentials
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import com.mosaicglobal.finance.feature.auth.domain.validation.CredentialRules

class LoginUseCase(private val repository: AuthRepository) {

    suspend operator fun invoke(email: String, password: String): AppResult<Session> {
        val trimmedEmail = email.trim()
        val errors = listOfNotNull(
            CredentialRules.validateEmail(trimmedEmail),
            CredentialRules.validateLoginPassword(password),
        )
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))
        return repository.login(Credentials(email = trimmedEmail, password = password))
    }
}
