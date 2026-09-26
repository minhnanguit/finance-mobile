package com.mosaicglobal.finance.feature.auth.data.repository

import co.touchlab.kermit.Logger
import com.mosaicglobal.finance.core.auth.AuthorizationOutcome
import com.mosaicglobal.finance.core.auth.OidcAuthenticator
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.common.result.map
import com.mosaicglobal.finance.core.common.result.onFailure
import com.mosaicglobal.finance.core.common.result.onSuccess
import com.mosaicglobal.finance.core.common.time.Clock
import com.mosaicglobal.finance.core.datastore.session.SessionStore
import com.mosaicglobal.finance.core.network.auth.TokenCache
import com.mosaicglobal.finance.feature.auth.data.mapper.toDomain
import com.mosaicglobal.finance.feature.auth.data.mapper.toPrompt
import com.mosaicglobal.finance.feature.auth.data.mapper.toStoredSession
import com.mosaicglobal.finance.feature.auth.data.remote.UserRemoteDataSource
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.SignInMode
import com.mosaicglobal.finance.feature.auth.domain.model.SignInResult
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AuthRepositoryImpl(
    private val authenticator: OidcAuthenticator,
    private val userRemote: UserRemoteDataSource,
    private val sessionStore: SessionStore,
    private val tokenCache: TokenCache,
    private val clock: Clock,
    private val logger: Logger,
) : AuthRepository {

    override suspend fun signIn(mode: SignInMode): AppResult<SignInResult> =
        authenticator.authorize(mode.toPrompt()).map { outcome ->
            when (outcome) {
                AuthorizationOutcome.Cancelled -> SignInResult.Cancelled
                is AuthorizationOutcome.Authorized -> {
                    val stored = outcome.tokens.toStoredSession(now = clock.now())
                    sessionStore.save(stored)
                    tokenCache.invalidate()
                    SignInResult.SignedIn(stored.toDomain())
                }
            }
        }

    override suspend fun logout(): AppResult<Unit> {
        sessionStore.current()?.let { session ->
            // Best effort: mất mạng hay Keycloak lỗi cũng KHÔNG được giữ user đăng nhập ở local.
            authenticator.endSession(session.refreshToken).onFailure { logger.w { "Logout phía IdP thất bại: $it" } }
        }
        sessionStore.clear()
        tokenCache.invalidate()
        return AppResult.Success(Unit)
    }

    override fun observeSession(): Flow<Session?> = sessionStore.session.map { it?.toDomain() }

    override suspend fun currentUser(): AppResult<UserProfile> = userRemote.currentUser()
        .map { it.toDomain() }
        .onSuccess { profile -> sessionStore.attachUserId(profile.id) }
}
