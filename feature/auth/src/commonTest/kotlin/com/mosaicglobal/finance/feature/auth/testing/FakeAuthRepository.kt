package com.mosaicglobal.finance.feature.auth.testing

import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.SignInMode
import com.mosaicglobal.finance.feature.auth.domain.model.SignInResult
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAuthRepository : AuthRepository {
    val sessions = MutableStateFlow<Session?>(null)
    var signInResult: AppResult<SignInResult> = AppResult.Failure(AppError.Unknown("chưa cấu hình"))
    var currentUserResult: AppResult<UserProfile> = AppResult.Failure(AppError.Unknown("chưa cấu hình"))
    var logoutResult: AppResult<Unit> = AppResult.Success(Unit)

    val signInCalls = mutableListOf<SignInMode>()
    var logoutCalls = 0

    /** Gán vào thì `signIn` treo tới khi complete — để test thấy được trạng thái đang chờ browser. */
    var signInGate: CompletableDeferred<Unit>? = null

    override suspend fun signIn(mode: SignInMode): AppResult<SignInResult> {
        signInCalls += mode
        signInGate?.await()
        return signInResult
    }

    override suspend fun logout(): AppResult<Unit> {
        logoutCalls += 1
        sessions.value = null
        return logoutResult
    }

    override fun observeSession(): Flow<Session?> = sessions

    override suspend fun currentUser(): AppResult<UserProfile> = currentUserResult
}
