package com.mosaicglobal.finance.feature.auth.domain.usecase

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.SignInMode
import com.mosaicglobal.finance.feature.auth.domain.model.SignInResult
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository

/**
 * Không còn validate email/mật khẩu ở client: form nằm trên Keycloak, và password policy (≥ 12 ký
 * tự, brute force...) do realm quyết định.
 */
class SignInUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(mode: SignInMode = SignInMode.SignIn): AppResult<SignInResult> =
        repository.signIn(mode)
}
