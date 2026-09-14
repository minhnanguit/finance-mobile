package com.mosaicglobal.finance.feature.auth.testing

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.Credentials
import com.mosaicglobal.finance.feature.auth.domain.model.Registration
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAuthRepository : AuthRepository {
    val sessions = MutableStateFlow<Session?>(null)
    var loginResult: AppResult<Session> = AppResult.Failure(com.mosaicglobal.finance.core.common.result.AppError.Unknown("not configured"))
    var registerResult: AppResult<Session> = loginResult
    var currentUserResult: AppResult<UserProfile> = AppResult.Failure(com.mosaicglobal.finance.core.common.result.AppError.Unknown("not configured"))
    var logoutResult: AppResult<Unit> = AppResult.Success(Unit)

    val loginCalls = mutableListOf<Credentials>()
    val registerCalls = mutableListOf<Registration>()
    var logoutCalls = 0

    /** When set, `login` suspends until completed so tests can observe the submitting state. */
    var loginGate: CompletableDeferred<Unit>? = null

    override suspend fun register(registration: Registration): AppResult<Session> {
        registerCalls += registration
        return registerResult
    }

    override suspend fun login(credentials: Credentials): AppResult<Session> {
        loginCalls += credentials
        loginGate?.await()
        return loginResult
    }

    override suspend fun logout(): AppResult<Unit> {
        logoutCalls += 1
        sessions.value = null
        return logoutResult
    }

    override fun observeSession(): Flow<Session?> = sessions

    override suspend fun currentUser(): AppResult<UserProfile> = currentUserResult
}
