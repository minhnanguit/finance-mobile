package com.mosaicglobal.finance.feature.auth.domain.usecase

import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.Registration
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import com.mosaicglobal.finance.feature.auth.domain.validation.CredentialRules

class RegisterUseCase(private val repository: AuthRepository) {

    suspend operator fun invoke(email: String, password: String, displayName: String): AppResult<Session> {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()
        val errors = listOfNotNull(
            CredentialRules.validateEmail(trimmedEmail),
            CredentialRules.validateNewPassword(password),
            CredentialRules.validateDisplayName(trimmedName),
        )
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))
        return repository.register(Registration(email = trimmedEmail, password = password, displayName = trimmedName))
    }
}
