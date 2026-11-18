@file:OptIn(ExperimentalTime::class)

package com.uit.finance.feature.settings.data.repository

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
import com.uit.finance.core.ledger.testing.FakeOutboxRepository
import com.uit.finance.core.network.api.ApiException
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.network.auth.TokenCache
import com.uit.finance.core.network.auth.TokenProvider
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.SessionNotice
import com.uit.finance.core.session.UserSession
import com.uit.finance.feature.settings.data.remote.AccountRemoteDataSource
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.model.PasswordChange
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class AccountRepositoryImplTest {

    /** Phiên + token dùng chung một chỗ, compare-and-set theo refresh token như `SecureSessionStore`. */
    private class FakeSessionStore : SessionStore, TokenProvider {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session get() = state
        override suspend fun current() = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun attachUserId(userId: String, forRefreshToken: String) = true
        override suspend fun clear() { state.value = null }
        override suspend fun tokens(): AuthTokens? = state.value?.let { AuthTokens(it.accessToken, it.refreshToken, 300) }
        override suspend fun replaceIfCurrent(refreshedFrom: String, tokens: AuthTokens): Boolean {
            val existing = state.value ?: return false
            if (existing.refreshToken != refreshedFrom) return false
            state.value = existing.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken)
            return true
        }
        override suspend fun clearIfCurrent(rejectedRefreshToken: String) = false
    }

    private class FakeAuthenticator : OidcAuthenticator {
        var reauth: AppResult<ReauthenticationOutcome> = AppResult.Success(ReauthenticationOutcome.Cancelled)
        var endSessionResult: AppResult<Unit> = AppResult.Success(Unit)
        val endedSessions = mutableListOf<String>()
        var reauthCalls = 0
        /** Chạy trong lúc "trình duyệt" đang mở. */
        var whileBrowsing: suspend () -> Unit = {}

        override suspend fun authorize(prompt: AuthorizationPrompt): AppResult<AuthorizationOutcome> =
            AppResult.Success(AuthorizationOutcome.Cancelled)
        override suspend fun reauthenticate(currentAccessToken: String): AppResult<ReauthenticationOutcome> {
            reauthCalls += 1
            whileBrowsing()
            return reauth
        }
        override suspend fun endSession(refreshToken: String): AppResult<Unit> {
            endedSessions += refreshToken
            return endSessionResult
        }

        var passwordChange: AppResult<PasswordChangeOutcome> = AppResult.Success(PasswordChangeOutcome.Cancelled)
        val passwordChangeTokens = mutableListOf<String>()
        override suspend fun changePassword(currentAccessToken: String): AppResult<PasswordChangeOutcome> {
            passwordChangeTokens += currentAccessToken
            whileBrowsing()
            return passwordChange
        }
    }

    private class FakeUserApi : UserApi {
        var deleteFailure: Exception? = null
        var profileFailure: Exception? = null
        /** Giữ `DELETE /me` lại giữa chừng, như request đang trên đường. */
        var deleteGate: CompletableDeferred<Unit>? = null
        val deleteAuthorizations = mutableListOf<String?>()
        var currentAccessToken: () -> String? = { null }
        override suspend fun getCurrentUser(): UserProfileDto {
            profileFailure?.let { throw it }
            return UserProfileDto("user-1", "ann@example.com", "Ann", Instant.fromEpochSeconds(1_700_000_000))
        }
        override suspend fun deleteCurrentUser() {
            deleteAuthorizations += currentAccessToken()
            deleteGate?.await()
            deleteFailure?.let { throw it }
        }
    }

    private class FakeUserSession : UserSession {
        val events = mutableListOf<String>()
        override val state = MutableStateFlow<LocalDataState>(LocalDataState.Ready("user-1"))
        override val notice = MutableStateFlow<SessionNotice?>(null)
        override fun start() = Unit
        override fun refresh() = Unit
        override fun acknowledgeNotice() = Unit
        override suspend fun wipeCurrentUser() { events += "wipe" }
        override suspend fun discardDeletedAccount() { events += "discardDeleted" }
        override suspend fun otherUsersOnDevice(currentUserId: String) = emptySet<String>()
        override suspend fun usersOnDevice() = emptySet<String>()
        override suspend fun wipe(userIds: Set<String>) = Unit
    }

    private val sessions = FakeSessionStore()
    private val authenticator = FakeAuthenticator()
    private val userApi = FakeUserApi().also { api -> api.currentAccessToken = { sessions.state.value?.accessToken } }
    private val userSession = FakeUserSession()
    private var tokenCacheInvalidations = 0

    private fun repository() = AccountRepositoryImpl(
        remote = AccountRemoteDataSource(userApi),
        sessions = sessions,
        tokens = sessions,
        tokenCache = TokenCache { tokenCacheInvalidations++ },
        authenticator = authenticator,
        userSession = userSession,
        outbox = FakeOutboxRepository(),
        logger = Logger.withTag("test"),
    )

    private val oldToken = jwt(sub = "kc-ann", email = "ann@example.com", name = "Ann")
    private val freshTokens = AuthTokens("fresh-access", "fresh-refresh", 300)

    private fun signedIn() {
        sessions.state.value = StoredSession(oldToken, "old-refresh", 0, userId = "user-1")
    }

    @Test
    fun `đổi mật khẩu xong thì phiên trên máy chuyển sang token mới - không bị đăng xuất`() = runTest {
        signedIn()
        authenticator.passwordChange = AppResult.Success(PasswordChangeOutcome.Changed(freshTokens))

        val result = repository().changePassword()

        assertEquals(AppResult.Success(PasswordChange.Changed), result)
        assertEquals(listOf(oldToken), authenticator.passwordChangeTokens)
        assertEquals("fresh-refresh", sessions.state.value?.refreshToken)
        assertEquals("user-1", sessions.state.value?.userId)
        assertEquals(1, tokenCacheInvalidations)
        assertEquals(emptyList(), authenticator.endedSessions)
    }

    @Test
    fun `đổi mật khẩu trong tab riêng tạo phiên mới thì đóng phiên cũ vừa bị thay`() = runTest {
        sessions.state.value = StoredSession(jwt("kc-ann", "ann@example.com", "Ann", sid = "s-old"), "old-refresh", 0, userId = "user-1")
        val fresh = AuthTokens(jwt("kc-ann", "ann@example.com", "Ann", sid = "s-new"), "new-refresh", 300)
        authenticator.passwordChange = AppResult.Success(PasswordChangeOutcome.Changed(fresh))

        assertEquals(AppResult.Success(PasswordChange.Changed), repository().changePassword())

        assertEquals("new-refresh", sessions.state.value?.refreshToken)
        assertEquals(listOf("old-refresh"), authenticator.endedSessions, "phiên cũ không còn ai dùng")
    }

    @Test
    fun `phiên mới trùng phiên cũ thì không đóng - trình duyệt không hỗ trợ tab riêng`() = runTest {
        sessions.state.value = StoredSession(jwt("kc-ann", "ann@example.com", "Ann", sid = "s-1"), "old-refresh", 0, userId = "user-1")
        val fresh = AuthTokens(jwt("kc-ann", "ann@example.com", "Ann", sid = "s-1"), "new-refresh", 300)
        authenticator.passwordChange = AppResult.Success(PasswordChangeOutcome.Changed(fresh))

        repository().changePassword()

        assertEquals("new-refresh", sessions.state.value?.refreshToken)
        assertEquals(emptyList(), authenticator.endedSessions, "đóng phiên cũ là đóng luôn phiên mới")
    }

    @Test
    fun `huỷ đổi mật khẩu hoặc đổi nhầm tài khoản khác thì giữ nguyên phiên`() = runTest {
        signedIn()

        for (outcome in listOf(PasswordChangeOutcome.Cancelled, PasswordChangeOutcome.DifferentAccount)) {
            authenticator.passwordChange = AppResult.Success(outcome)
            val result = repository().changePassword()
            val expected = if (outcome == PasswordChangeOutcome.Cancelled) PasswordChange.Cancelled else PasswordChange.DifferentAccount
            assertEquals(AppResult.Success(expected), result)
        }
        assertEquals("old-refresh", sessions.state.value?.refreshToken)
        assertEquals(0, tokenCacheInvalidations)
    }

    @Test
    fun `đăng xuất trong lúc đổi mật khẩu thì không gắn token mới - huỷ phiên vừa mở`() = runTest {
        signedIn()
        authenticator.passwordChange = AppResult.Success(PasswordChangeOutcome.Changed(freshTokens))
        authenticator.whileBrowsing = { sessions.clear() }

        val result = repository().changePassword()

        assertEquals(AppResult.Success(PasswordChange.Changed), result)
        assertNull(sessions.state.value)
        assertEquals(listOf("fresh-refresh"), authenticator.endedSessions)
    }

    @Test
    fun `đổi mật khẩu khi không còn phiên thì báo hết phiên - không mở trình duyệt`() = runTest {
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository().changePassword())
        assertEquals(emptyList(), authenticator.passwordChangeTokens)
    }

    @Test
    fun `xác nhận đúng tài khoản thì xoá bằng token vừa đăng nhập lại rồi dọn mọi thứ trên máy`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))

        val result = repository().deleteAccount()

        assertEquals(AppResult.Success(AccountDeletion.Deleted), result)
        assertEquals(listOf<String?>("fresh-access"), userApi.deleteAuthorizations, "DELETE /me phải mang auth_time mới")
        assertEquals(1, authenticator.reauthCalls)
        assertEquals(listOf("discardDeleted"), userSession.events)
        assertEquals(listOf("fresh-refresh"), authenticator.endedSessions)
        assertEquals(1, tokenCacheInvalidations)
    }

    @Test
    fun `server báo đã xoá rồi thì coi như xong - lần trước chưa kịp nhận kết quả`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
        userApi.deleteFailure = ApiException(403, "identity.user_deleted", "Forbidden", null)

        assertEquals(AppResult.Success(AccountDeletion.Deleted), repository().deleteAccount())
        assertEquals(listOf("discardDeleted"), userSession.events)
    }

    @Test
    fun `xác nhận đã quá hạn khi tới server thì báo xác nhận lại - không xoá gì trên máy`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
        userApi.deleteFailure = ApiException(403, "identity.reauth_required", "Forbidden", null)

        assertEquals(AppResult.Success(AccountDeletion.ConfirmationExpired), repository().deleteAccount())
        assertEquals(emptyList(), userSession.events)
    }

    @Test
    fun `mất mạng lúc gửi xoá - hỏi lại thấy tài khoản còn thì trả lỗi và giữ nguyên sổ`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
        userApi.deleteFailure = IOException("offline")

        val result = repository().deleteAccount()

        assertEquals(AppError.Network("offline"), (result as AppResult.Failure).error)
        assertEquals(emptyList(), userSession.events)
        assertEquals("fresh-refresh", sessions.state.value?.refreshToken, "phiên đã mang token mới")
    }

    @Test
    fun `đứt kết nối sau khi server đã xoá - hỏi lại nhận user_deleted thì coi như xong`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
        userApi.deleteFailure = IOException("timeout")
        userApi.profileFailure = ApiException(403, "identity.user_deleted", "Forbidden", null)

        assertEquals(AppResult.Success(AccountDeletion.Deleted), repository().deleteAccount())
        assertEquals(listOf("discardDeleted"), userSession.events)
    }

    @Test
    fun `server lỗi và hỏi lại cũng không được thì báo chưa rõ - không xoá sổ`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
        userApi.deleteFailure = ApiException(503, null, "Service Unavailable", null)
        userApi.profileFailure = IOException("offline")

        assertEquals(AppResult.Success(AccountDeletion.Unconfirmed), repository().deleteAccount())
        assertEquals(emptyList(), userSession.events)
    }

    @Test
    fun `sync nền xoay refresh token của chính user lúc trình duyệt mở thì vẫn xoá - không huỷ phiên SSO giữa chừng`() =
        runTest {
            signedIn()
            authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
            authenticator.whileBrowsing = {
                sessions.state.value = StoredSession("rotated-access", "rotated-refresh", 0, userId = "user-1")
            }

            assertEquals(AppResult.Success(AccountDeletion.Deleted), repository().deleteAccount())
            assertEquals(listOf<String?>("fresh-access"), userApi.deleteAuthorizations)
            assertEquals(listOf("fresh-refresh"), authenticator.endedSessions, "chỉ huỷ phiên sau khi đã xoá xong")
        }

    @Test
    fun `màn bị đóng lúc yêu cầu xoá đang đi thì vẫn dọn xong trên máy`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
        val inFlight = CompletableDeferred<Unit>()
        userApi.deleteGate = inFlight

        val job = launch { repository().deleteAccount() }
        runCurrent()
        job.cancel()
        inFlight.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("discardDeleted"), userSession.events)
    }

    @Test
    fun `đóng trình duyệt hoặc đăng nhập nhầm tài khoản thì không gọi xoá`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Cancelled)
        assertEquals(AppResult.Success(AccountDeletion.Cancelled), repository().deleteAccount())

        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.DifferentAccount)
        assertEquals(AppResult.Success(AccountDeletion.DifferentAccount), repository().deleteAccount())

        assertEquals(emptyList(), userApi.deleteAuthorizations)
        assertEquals("old-refresh", sessions.state.value?.refreshToken)
    }

    @Test
    fun `phiên đổi trong lúc đăng nhập lại thì không xoá ai và huỷ phiên vừa mở - B5`() = runTest {
        signedIn()
        authenticator.reauth = AppResult.Success(ReauthenticationOutcome.Confirmed(freshTokens))
        authenticator.whileBrowsing = { sessions.state.value = StoredSession("b-access", "b-refresh", 0, userId = "user-b") }

        val result = repository().deleteAccount()

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertEquals(emptyList(), userApi.deleteAuthorizations)
        assertEquals("b-refresh", sessions.state.value?.refreshToken, "phiên của người mới không bị đụng")
        assertEquals(listOf("fresh-refresh"), authenticator.endedSessions)
    }

    @Test
    fun `chưa đăng nhập thì không mở trình duyệt`() = runTest {
        assertEquals(AppResult.Failure(AppError.Unauthorized), repository().deleteAccount())
        assertEquals(0, authenticator.reauthCalls)
    }

    @Test
    fun `đăng xuất xoá sổ trước rồi mới xoá phiên - kể cả khi Keycloak lỗi`() = runTest {
        signedIn()
        authenticator.endSessionResult = AppResult.Failure(AppError.Network("offline"))

        assertEquals(AppResult.Success(Unit), repository().logout())

        assertEquals(listOf("wipe"), userSession.events)
        assertNull(sessions.state.value)
        assertEquals(listOf("old-refresh"), authenticator.endedSessions)
        assertEquals(1, tokenCacheInvalidations)
    }

    @Test
    fun `hồ sơ có ngay từ token rồi được thay bằng bản của server`() = runTest {
        signedIn()

        val emitted = repository().profile().toList()

        assertEquals(listOf("Ann", "Ann"), emitted.map { it.displayName })
        assertNull(emitted.first().memberSince, "token không có ngày tham gia")
        assertEquals(Instant.fromEpochSeconds(1_700_000_000), emitted.last().memberSince)
    }

    private fun jwt(sub: String, email: String, name: String, sid: String? = null): String {
        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val session = sid?.let { ""","sid":"$it"""" }.orEmpty()
        val payload = """{"sub":"$sub","email":"$email","name":"$name"$session}"""
        return "${base64.encode("{}".encodeToByteArray())}.${base64.encode(payload.encodeToByteArray())}.sig"
    }
}
