@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.feature.auth.data.repository

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import com.mosaicglobal.finance.core.common.platform.Platform
import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.datastore.session.SessionStore
import com.mosaicglobal.finance.core.datastore.session.StoredSession
import com.mosaicglobal.finance.core.network.api.ApiException
import com.mosaicglobal.finance.core.network.api.AuthApi
import com.mosaicglobal.finance.core.network.api.UserApi
import com.mosaicglobal.finance.core.network.api.model.DevicePlatformDto
import com.mosaicglobal.finance.core.network.api.model.LoginRequestDto
import com.mosaicglobal.finance.core.network.api.model.LogoutRequestDto
import com.mosaicglobal.finance.core.network.api.model.RefreshRequestDto
import com.mosaicglobal.finance.core.network.api.model.RegisterRequestDto
import com.mosaicglobal.finance.core.network.api.model.TokenPairDto
import com.mosaicglobal.finance.core.network.api.model.UserProfileDto
import com.mosaicglobal.finance.core.network.auth.TokenCache
import com.mosaicglobal.finance.core.testing.TestClock
import com.mosaicglobal.finance.feature.auth.data.remote.AuthRemoteDataSource
import com.mosaicglobal.finance.feature.auth.domain.model.Credentials
import com.mosaicglobal.finance.feature.auth.domain.model.DeviceInfo
import com.mosaicglobal.finance.feature.auth.domain.model.Registration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AuthRepositoryImplTest {

    private class FakeAuthApi : AuthApi {
        var tokenPair = TokenPairDto("access-1", "refresh-1", "Bearer", 900)
        var failWith: ApiException? = null
        val loginRequests = mutableListOf<LoginRequestDto>()
        val registerRequests = mutableListOf<RegisterRequestDto>()
        val logoutRequests = mutableListOf<LogoutRequestDto>()

        override suspend fun register(request: RegisterRequestDto): TokenPairDto {
            registerRequests += request
            failWith?.let { throw it }
            return tokenPair
        }

        override suspend fun login(request: LoginRequestDto): TokenPairDto {
            loginRequests += request
            failWith?.let { throw it }
            return tokenPair
        }

        override suspend fun refresh(request: RefreshRequestDto): TokenPairDto = tokenPair

        override suspend fun logout(request: LogoutRequestDto) {
            logoutRequests += request
            failWith?.let { throw it }
        }
    }

    private class FakeUserApi : UserApi {
        var profile = UserProfileDto("user-1", "user@example.com", "Nam", Instant.fromEpochSeconds(1_600_000_000))
        var failWith: ApiException? = null
        override suspend fun getCurrentUser(): UserProfileDto {
            failWith?.let { throw it }
            return profile
        }
    }

    private class FakeSessionStore : SessionStore {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session: Flow<StoredSession?> get() = state
        override suspend fun current(): StoredSession? = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun attachUserId(userId: String) { state.value = state.value?.copy(userId = userId) }
        override suspend fun clear() { state.value = null }
    }

    private class RecordingTokenCache : TokenCache {
        var invalidations = 0
        override fun invalidate() { invalidations += 1 }
    }

    private val authApi = FakeAuthApi()
    private val userApi = FakeUserApi()
    private val sessionStore = FakeSessionStore()
    private val tokenCache = RecordingTokenCache()
    private val clock = TestClock(Instant.fromEpochSeconds(1_000))
    private val device = DeviceInfo("install-1234", "Pixel 9", Platform.ANDROID)

    private fun repository() = AuthRepositoryImpl(
        remote = AuthRemoteDataSource(authApi, userApi),
        sessionStore = sessionStore,
        tokenCache = tokenCache,
        deviceInfoProvider = { device },
        clock = clock,
        logger = Logger.withTag("test"),
    )

    @Test
    fun loginStoresSessionWithDeviceAndExpiry() = runTest {
        val result = repository().login(Credentials("user@example.com", "hunter22"))

        val session = assertIs<AppResult.Success<*>>(result).value
        assertEquals(Instant.fromEpochSeconds(1_900), (session as com.mosaicglobal.finance.feature.auth.domain.model.Session).accessTokenExpiresAt)
        assertNull(session.userId)

        val stored = sessionStore.state.value
        assertEquals("access-1", stored?.accessToken)
        assertEquals("refresh-1", stored?.refreshToken)
        assertEquals(1_900L, stored?.accessTokenExpiresAtEpochSeconds)
        assertEquals(1, tokenCache.invalidations)

        val request = authApi.loginRequests.single()
        assertEquals("install-1234", request.device.deviceId)
        assertEquals(DevicePlatformDto.ANDROID, request.device.platform)
    }

    @Test
    fun registerMapsAllFields() = runTest {
        repository().register(Registration("new@example.com", "hunter22", "Nam"))

        val request = authApi.registerRequests.single()
        assertEquals("new@example.com", request.email)
        assertEquals("Nam", request.displayName)
        assertEquals("Pixel 9", request.device.deviceName)
    }

    @Test
    fun apiProblemBecomesAppErrorWithoutThrowing() = runTest {
        authApi.failWith = ApiException(status = 409, code = "identity.email_taken", title = "Conflict", detail = "Email already registered")

        val result = repository().register(Registration("dup@example.com", "hunter22", "Nam"))

        val error = assertIs<AppResult.Failure>(result).error
        assertEquals(AppError.Api(409, "identity.email_taken", "Conflict", "Email already registered"), error)
        assertNull(sessionStore.state.value)
        assertEquals(0, tokenCache.invalidations)
    }

    @Test
    fun unauthorizedIsDistinctError() = runTest {
        authApi.failWith = ApiException(status = 401, code = "identity.bad_credentials", title = "Unauthorized", detail = null)
        val result = repository().login(Credentials("user@example.com", "wrong"))
        assertEquals(AppError.Unauthorized, assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun currentUserAttachesUserIdToSession() = runTest {
        sessionStore.save(StoredSession("a", "r", 5_000L))

        val result = repository().currentUser()

        val profile = assertIs<AppResult.Success<*>>(result).value as com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
        assertEquals("user-1", profile.id)
        assertEquals("user-1", sessionStore.state.value?.userId)
    }

    @Test
    fun logoutClearsLocallyEvenWhenServerFails() = runTest {
        sessionStore.save(StoredSession("a", "refresh-old", 5_000L, userId = "u"))
        authApi.failWith = ApiException(status = 500, code = null, title = "Internal Server Error", detail = null)
        val repo = repository()

        repo.observeSession().test {
            assertEquals("u", awaitItem()?.userId)
            assertEquals(AppResult.Success(Unit), repo.logout())
            assertNull(awaitItem())
        }
        assertEquals("refresh-old", authApi.logoutRequests.single().refreshToken)
        assertEquals(1, tokenCache.invalidations)
    }
}
