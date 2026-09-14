package com.mosaicglobal.finance.feature.auth.data.repository

import co.touchlab.kermit.Logger
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.common.result.map
import com.mosaicglobal.finance.core.common.result.onFailure
import com.mosaicglobal.finance.core.common.result.onSuccess
import com.mosaicglobal.finance.core.common.time.Clock
import com.mosaicglobal.finance.core.datastore.session.SessionStore
import com.mosaicglobal.finance.core.network.api.model.TokenPairDto
import com.mosaicglobal.finance.core.network.auth.TokenCache
import com.mosaicglobal.finance.feature.auth.data.mapper.toDomain
import com.mosaicglobal.finance.feature.auth.data.mapper.toDto
import com.mosaicglobal.finance.feature.auth.data.mapper.toStoredSession
import com.mosaicglobal.finance.feature.auth.data.remote.AuthRemoteDataSource
import com.mosaicglobal.finance.feature.auth.domain.model.Credentials
import com.mosaicglobal.finance.feature.auth.domain.model.Registration
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import com.mosaicglobal.finance.feature.auth.domain.repository.DeviceInfoProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val sessionStore: SessionStore,
    private val tokenCache: TokenCache,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val clock: Clock,
    private val logger: Logger,
) : AuthRepository {

    override suspend fun register(registration: Registration): AppResult<Session> {
        val device = deviceInfoProvider.current().toDto()
        return remote.register(registration.email, registration.password, registration.displayName, device)
            .openSession()
    }

    override suspend fun login(credentials: Credentials): AppResult<Session> {
        val device = deviceInfoProvider.current().toDto()
        return remote.login(credentials.email, credentials.password, device).openSession()
    }

    override suspend fun logout(): AppResult<Unit> {
        val session = sessionStore.current()
        if (session != null) {
            // Best effort: the server also answers 204 for tokens that are already invalid, and a
            // network failure must never keep the user signed in locally.
            remote.logout(session.refreshToken).onFailure { logger.w { "Remote logout failed: $it" } }
        }
        sessionStore.clear()
        tokenCache.invalidate()
        return AppResult.Success(Unit)
    }

    override fun observeSession(): Flow<Session?> = sessionStore.session.map { it?.toDomain() }

    override suspend fun currentUser(): AppResult<UserProfile> = remote.currentUser()
        .map { it.toDomain() }
        .onSuccess { profile -> sessionStore.attachUserId(profile.id) }

    private suspend fun AppResult<TokenPairDto>.openSession(): AppResult<Session> = map { tokens ->
        val stored = tokens.toStoredSession(now = clock.now(), userId = null)
        sessionStore.save(stored)
        tokenCache.invalidate()
        stored.toDomain()
    }
}
