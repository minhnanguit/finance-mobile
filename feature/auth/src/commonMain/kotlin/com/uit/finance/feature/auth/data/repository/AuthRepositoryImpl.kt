package com.uit.finance.feature.auth.data.repository

import co.touchlab.kermit.Logger
import com.uit.finance.core.auth.AuthorizationOutcome
import com.uit.finance.core.auth.OidcAuthenticator
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.map
import com.uit.finance.core.common.result.onSuccess
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.network.auth.TokenCache
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.SessionNotice
import com.uit.finance.core.session.UserSession
import com.uit.finance.feature.auth.data.mapper.toDomain
import com.uit.finance.feature.auth.data.mapper.toPrompt
import com.uit.finance.feature.auth.data.mapper.toStoredSession
import com.uit.finance.feature.auth.data.remote.UserRemoteDataSource
import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.SignOutNotice
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

    override fun observeSession(): Flow<Session?> = sessionStore.session.map { it?.toDomain() }

    /** `userId` chỉ được gắn vào đúng phiên đã hỏi `/me` (phiên đổi giữa chừng thì bỏ, ADR-006 B5). */
    override suspend fun currentUser(): AppResult<UserProfile> {
        val askedFor = sessionStore.current()?.refreshToken
        return userRemote.currentUser()
            .map { it.toDomain() }
            .onSuccess { profile -> askedFor?.let { sessionStore.attachUserId(profile.id, forRefreshToken = it) } }
    }

    override fun observeLocalDataStatus(): Flow<LocalDataStatus> = userSession.state.map { state ->
        when (state) {
            LocalDataState.SignedOut, LocalDataState.ResolvingUser -> LocalDataStatus.Preparing
            is LocalDataState.Ready -> LocalDataStatus.Ready
            is LocalDataState.Failed ->
                if (state.error is AppError.Network) LocalDataStatus.NeedsNetwork else LocalDataStatus.Unavailable
        }
    }

    override fun retryLocalData() = userSession.refresh()

    override suspend fun otherAccountsOnDevice(): AppResult<Int> =
        currentUser().map { profile -> userSession.otherUsersOnDevice(profile.id).size }

    override fun observeSignOutNotice(): Flow<SignOutNotice?> = userSession.notice.map { notice ->
        when (notice) {
            SessionNotice.AccountDeleted -> SignOutNotice.AccountDeleted
            null -> null
        }
    }

    override fun acknowledgeSignOutNotice() = userSession.acknowledgeNotice()

    override suspend fun hasLocalDataWithoutSession(): Boolean =
        sessionStore.current() == null && userSession.usersOnDevice().isNotEmpty()

    override suspend fun wipeLocalData(): AppResult<Unit> {
        // Đang có phiên thì đường xoá là Đăng xuất / Xoá tài khoản, không phải nút này.
        if (sessionStore.current() != null) return AppResult.Success(Unit)
        userSession.wipe(userSession.usersOnDevice())
        return AppResult.Success(Unit)
    }

    override suspend fun wipeOtherAccounts(): AppResult<Unit> {
        val userId = sessionStore.current()?.userId ?: return AppResult.Failure(AppError.Unauthorized)
        userSession.wipe(userSession.otherUsersOnDevice(userId))
        return AppResult.Success(Unit)
    }
}
