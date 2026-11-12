@file:OptIn(ExperimentalTime::class)

package com.uit.finance.feature.auth.data.repository

import app.cash.turbine.test
import co.touchlab.kermit.Logger
import com.uit.finance.core.auth.AuthorizationOutcome
import com.uit.finance.core.auth.AuthorizationPrompt
import com.uit.finance.core.auth.OidcAuthenticator
import com.uit.finance.core.auth.PasswordChangeOutcome
import com.uit.finance.core.auth.ReauthenticationOutcome
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.datastore.session.StoredSession
import com.uit.finance.core.network.api.ApiException
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.network.auth.TokenCache
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.SessionNotice
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.testing.TestClock
import com.uit.finance.feature.auth.data.remote.UserRemoteDataSource
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.SignOutNotice
import com.uit.finance.feature.auth.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class AuthRepositoryImplTest {

    private class FakeAuthenticator : OidcAuthenticator {
        var outcome: AppResult<AuthorizationOutcome> =
            AppResult.Success(AuthorizationOutcome.Authorized(AuthTokens("access-1", "refresh-1", 300)))
        var endSessionResult: AppResult<Unit> = AppResult.Success(Unit)
        val prompts = mutableListOf<AuthorizationPrompt>()
        val endedSessions = mutableListOf<String>()

        override suspend fun authorize(prompt: AuthorizationPrompt): AppResult<AuthorizationOutcome> {
            prompts += prompt
            return outcome
        }

        override suspend fun endSession(refreshToken: String): AppResult<Unit> {
            endedSessions += refreshToken
            return endSessionResult
        }

        override suspend fun reauthenticate(currentAccessToken: String): AppResult<ReauthenticationOutcome> =
            AppResult.Success(ReauthenticationOutcome.Cancelled)

        override suspend fun changePassword(currentAccessToken: String): AppResult<PasswordChangeOutcome> =
            AppResult.Success(PasswordChangeOutcome.Cancelled)
    }

    private class FakeUserApi : UserApi {
        var profile = UserProfileDto("user-1", "user@example.com", "Nam", Instant.fromEpochSeconds(1_600_000_000))
        var failWith: ApiException? = null
        override suspend fun getCurrentUser(): UserProfileDto {
            failWith?.let { throw it }
            return profile
        }

        override suspend fun deleteCurrentUser() = Unit
    }

    private class FakeSessionStore : SessionStore {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session: Flow<StoredSession?> get() = state
        override suspend fun current(): StoredSession? = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun attachUserId(userId: String, forRefreshToken: String): Boolean {
            if (state.value?.refreshToken != forRefreshToken) return false
            state.value = state.value?.copy(userId = userId)
            return true
        }
        override suspend fun clear() { state.value = null }
    }

    private class RecordingTokenCache : TokenCache {
        var invalidations = 0
        override fun invalidate() { invalidations += 1 }
    }

    /** Ghi thứ tự thao tác để kiểm "xoá sổ trước, xoá token sau". */
    private class FakeUserSession(private val sessionStore: FakeSessionStore) : UserSession {
        val events = mutableListOf<String>()
        val stored = mutableSetOf("user-1")
        override val state = MutableStateFlow<LocalDataState>(LocalDataState.SignedOut)
        override val notice = MutableStateFlow<SessionNotice?>(null)
        override fun start() = Unit
        override fun refresh() = Unit
        override fun acknowledgeNotice() { notice.value = null }
        override suspend fun wipeCurrentUser() {
            events += "wipe(sessionPresent=${sessionStore.state.value != null})"
        }
        override suspend fun discardDeletedAccount() { events += "discard" }
        override suspend fun otherUsersOnDevice(currentUserId: String) = stored - currentUserId
        override suspend fun usersOnDevice(): Set<String> = stored.toSet()
        override suspend fun wipe(userIds: Set<String>) {
            events += "wipe$userIds"
            stored -= userIds
        }
    }

    private val authenticator = FakeAuthenticator()
    private val userApi = FakeUserApi()
    private val sessionStore = FakeSessionStore()
    private val tokenCache = RecordingTokenCache()
    private val userSession = FakeUserSession(sessionStore)
    private val clock = TestClock(Instant.fromEpochSeconds(1_000))

    private fun repository() = AuthRepositoryImpl(
        authenticator = authenticator,
        userRemote = UserRemoteDataSource(userApi),
        sessionStore = sessionStore,
        tokenCache = tokenCache,
        userSession = userSession,
        clock = clock,
        logger = Logger.withTag("test"),
    )

    @Test
    fun signInStoresTheKeycloakTokensWithTheirExpiry() = runTest {
        val result = repository().signIn(SignInMode.SignIn)

        assertEquals(AppResult.Success(SignInResult.SignedIn(Session(null, Instant.fromEpochSeconds(1_300)))), result)
        assertEquals(StoredSession("access-1", "refresh-1", 1_300L, userId = null), sessionStore.state.value)
        assertEquals(1, tokenCache.invalidations, "Ktor phải đọc lại token mới")
        assertEquals(listOf(AuthorizationPrompt.SignIn), authenticator.prompts)
    }

    @Test
    fun signUpAsksForTheRegistrationForm() = runTest {
        repository().signIn(SignInMode.SignUp)

        assertEquals(listOf(AuthorizationPrompt.SignUp), authenticator.prompts)
    }

    @Test
    fun cancellingLeavesNoSessionBehind() = runTest {
        authenticator.outcome = AppResult.Success(AuthorizationOutcome.Cancelled)

        assertEquals(AppResult.Success(SignInResult.Cancelled), repository().signIn(SignInMode.SignIn))
        assertNull(sessionStore.state.value)
        assertEquals(0, tokenCache.invalidations)
    }

    @Test
    fun aFailedSignInLeavesNoSessionBehind() = runTest {
        authenticator.outcome = AppResult.Failure(AppError.Network("offline"))

        assertEquals(AppResult.Failure(AppError.Network("offline")), repository().signIn(SignInMode.SignIn))
        assertNull(sessionStore.state.value)
    }

    @Test
    fun currentUserAttachesTheInternalIdToTheSession() = runTest {
        sessionStore.save(StoredSession("a", "r", 5_000L))

        val profile = assertIs<AppResult.Success<UserProfile>>(repository().currentUser()).value

        assertEquals("user-1", profile.id)
        assertEquals("user-1", sessionStore.state.value?.userId)
    }

    @Test
    fun relaysTheSignOutNoticeOfTheSession() = runTest {
        val repo = repository()

        repo.observeSignOutNotice().test {
            assertNull(awaitItem())
            userSession.notice.value = SessionNotice.AccountDeleted
            assertEquals(SignOutNotice.AccountDeleted, awaitItem())
            repo.acknowledgeSignOutNotice()
            assertNull(awaitItem())
        }
    }

    @Test
    fun findsAndWipesOtherAccountsLedgersOnTheDevice() = runTest {
        sessionStore.save(StoredSession("a", "r", 5_000L))
        userSession.stored += "user-old"
        val repo = repository()

        val found = repo.otherAccountsOnDevice()
        repo.wipeOtherAccounts()

        assertEquals(AppResult.Success(1), found)
        assertEquals(setOf("user-1"), userSession.stored)
    }

    @Test
    fun leftoverLedgersWithoutASessionCanBeWipedButNotWhileSignedIn() = runTest {
        userSession.stored += "user-old"
        val repo = repository()

        assertTrue(repo.hasLocalDataWithoutSession(), "chưa đăng nhập mà máy còn sổ")
        assertEquals(AppResult.Success(Unit), repo.wipeLocalData())
        assertTrue(userSession.stored.isEmpty())
        assertFalse(repo.hasLocalDataWithoutSession())

        userSession.stored += "user-1"
        sessionStore.save(StoredSession("a", "r", 5_000L, userId = "user-1"))
        assertFalse(repo.hasLocalDataWithoutSession(), "đang có phiên thì không hiện lựa chọn này")
        repo.wipeLocalData()
        assertEquals(setOf("user-1"), userSession.stored, "đang đăng nhập thì nút này không xoá gì")
    }
}
