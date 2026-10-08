package com.uit.finance.feature.auth.data.repository

import co.touchlab.kermit.Logger
import com.uit.finance.core.auth.AuthorizationOutcome
import com.uit.finance.core.auth.OidcAuthenticator
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.map
import com.uit.finance.core.common.result.onFailure
import com.uit.finance.core.common.result.onSuccess
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.network.auth.TokenCache
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.sync.outbox.OutboxRepository
import com.uit.finance.feature.auth.data.mapper.toDomain
import com.uit.finance.feature.auth.data.mapper.toPrompt
import com.uit.finance.feature.auth.data.mapper.toStoredSession
import com.uit.finance.feature.auth.data.remote.UserRemoteDataSource
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.UserProfile
import com.uit.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AuthRepositoryImpl(
    private val authenticator: OidcAuthenticator,
    private val userRemote: UserRemoteDataSource,
    private val sessionStore: SessionStore,
    private val tokenCache: TokenCache,
    private val userSession: UserSession,
    private val outbox: OutboxRepository,
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
        // Xoá sổ trước khi xoá token: lúc này vẫn biết sổ nào là của user này (B5).
        userSession.wipeCurrentUser()
        sessionStore.clear()
        tokenCache.invalidate()
        return AppResult.Success(Unit)
    }

    override fun observeSession(): Flow<Session?> = sessionStore.session.map { it?.toDomain() }

    override suspend fun currentUser(): AppResult<UserProfile> = userRemote.currentUser()
        .map { it.toDomain() }
        .onSuccess { profile -> sessionStore.attachUserId(profile.id) }

    override suspend fun unsentChangeCount(): Long = outbox.unsentCount()

    override suspend fun otherAccountsOnDevice(): AppResult<Int> =
        currentUser().map { profile -> userSession.otherUsersOnDevice(profile.id).size }

    override suspend fun wipeOtherAccounts(): AppResult<Unit> {
        val userId = sessionStore.current()?.userId ?: return AppResult.Failure(AppError.Unauthorized)
        userSession.wipe(userSession.otherUsersOnDevice(userId))
        return AppResult.Success(Unit)
    }
}
