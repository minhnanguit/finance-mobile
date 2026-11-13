package com.uit.finance.feature.settings.data.repository

import co.touchlab.kermit.Logger
import com.uit.finance.core.auth.OidcAuthenticator
import com.uit.finance.core.auth.PasswordChangeOutcome
import com.uit.finance.core.auth.ReauthenticationOutcome
import com.uit.finance.core.auth.TokenClaims
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.flatMap
import com.uit.finance.core.common.result.onFailure
import com.uit.finance.core.common.result.onSuccess
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.datastore.session.StoredSession
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.network.auth.TokenCache
import com.uit.finance.core.network.auth.TokenProvider
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.sync.outbox.OutboxRepository
import com.uit.finance.feature.settings.data.mapper.isOutcomeUnknown
import com.uit.finance.feature.settings.data.mapper.isReauthRequired
import com.uit.finance.feature.settings.data.mapper.isUserDeleted
import com.uit.finance.feature.settings.data.mapper.toDomain
import com.uit.finance.feature.settings.data.remote.AccountRemoteDataSource
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.model.AccountProfile
import com.uit.finance.feature.settings.domain.model.PasswordChange
import com.uit.finance.feature.settings.domain.repository.AccountRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal class AccountRepositoryImpl(
    private val remote: AccountRemoteDataSource,
    private val sessions: SessionStore,
    private val tokens: TokenProvider,
    private val tokenCache: TokenCache,
    private val authenticator: OidcAuthenticator,
    private val userSession: UserSession,
    private val outbox: OutboxRepository,
    private val logger: Logger,
) : AccountRepository {

    override fun profile(): Flow<AccountProfile> = flow {
        sessions.current()?.accessToken?.let(TokenClaims::profile)?.let { emit(it.toDomain()) }
        remote.profile().onSuccess { emit(it.toDomain()) }
    }

    override suspend fun unsentChangeCount(): Long = outbox.unsentCount()

    override suspend fun logout(): AppResult<Unit> {
        // Best effort: mất mạng hay Keycloak lỗi cũng KHÔNG được giữ user đăng nhập ở local.
        sessions.current()?.let { endSessionQuietly(it.refreshToken) }
        // Xoá sổ trước khi xoá token: lúc này vẫn biết sổ nào là của user này (B5).
        userSession.wipeCurrentUser()
        sessions.clear()
        tokenCache.invalidate()
        return AppResult.Success(Unit)
    }

    override suspend fun deleteAccount(): AppResult<AccountDeletion> {
        val session = sessions.current() ?: return AppResult.Failure(AppError.Unauthorized)
        return authenticator.reauthenticate(session.accessToken).flatMap { outcome ->
            when (outcome) {
                ReauthenticationOutcome.Cancelled -> AppResult.Success(AccountDeletion.Cancelled)
                ReauthenticationOutcome.DifferentAccount -> AppResult.Success(AccountDeletion.DifferentAccount)
                // Từ đây không được dừng giữa chừng (user bấm back, màn bị đóng): DELETE đã đi thì phải dọn xong máy.
                is ReauthenticationOutcome.Confirmed ->
                    withContext(NonCancellable) { deleteWith(session, outcome.tokens) }
            }
        }
    }

    override suspend fun changePassword(): AppResult<PasswordChange> {
        val session = sessions.current() ?: return AppResult.Failure(AppError.Unauthorized)
        return authenticator.changePassword(session.accessToken).flatMap { outcome ->
            when (outcome) {
                PasswordChangeOutcome.Cancelled -> AppResult.Success(PasswordChange.Cancelled)
                PasswordChangeOutcome.DifferentAccount -> AppResult.Success(PasswordChange.DifferentAccount)
                // Mật khẩu đã đổi trên Keycloak: dù màn bị đóng vẫn phải gắn token mới, không thì phiên cũ (có thể
                // đã bị "Đăng xuất khỏi thiết bị khác" huỷ) làm user bị đá ra ở lần refresh kế tiếp.
                is PasswordChangeOutcome.Changed -> withContext(NonCancellable) {
                    val replaced = adoptFreshTokens(session, outcome.tokens)
                    if (replaced != null) {
                        tokenCache.invalidate()
                        endReplacedSession(replaced, outcome.tokens)
                    } else {
                        logger.i { "Phiên đổi trong lúc đổi mật khẩu; bỏ token mới" }
                        if (sessions.current()?.userId != session.userId) endSessionQuietly(outcome.tokens.refreshToken)
                    }
                    AppResult.Success(PasswordChange.Changed)
                }
            }
        }
    }

    /** Gắn token vừa xác nhận vào phiên để `DELETE /me` mang `auth_time` mới, gọi xoá, rồi dọn mọi thứ trên máy (A9). */
    private suspend fun deleteWith(confirmedFor: StoredSession, fresh: AuthTokens): AppResult<AccountDeletion> {
        if (adoptFreshTokens(confirmedFor, fresh) == null) {
            logger.i { "Phiên đổi trong lúc xác nhận xoá tài khoản; không xoá" }
            // Token vừa nhận thuộc phiên SSO của chính user cần xoá. Chỉ huỷ khi app không còn giữ user đó: huỷ lúc
            // vẫn là user đó sẽ đăng xuất luôn phiên đang dùng (Keycloak dùng chung phiên SSO cho lần đăng nhập lại).
            if (sessions.current()?.userId != confirmedFor.userId) endSessionQuietly(fresh.refreshToken)
            return AppResult.Failure(AppError.Unauthorized)
        }
        tokenCache.invalidate()

        when (val result = remote.deleteAccount()) {
            is AppResult.Success -> Unit
            is AppResult.Failure -> when {
                // Lần trước đã xoá xong mà app chưa kịp nhận kết quả: coi như xong.
                result.error.isUserDeleted() -> Unit
                result.error.isReauthRequired() -> return AppResult.Success(AccountDeletion.ConfirmationExpired)
                // Đứt kết nối / 5xx: có thể server đã xoá rồi mới lỗi. Hỏi lại bằng chính token mới (còn hạn 5 phút).
                result.error.isOutcomeUnknown() -> when (wasDeleted()) {
                    true -> Unit
                    false -> return result
                    null -> return AppResult.Success(AccountDeletion.Unconfirmed)
                }
                else -> return result
            }
        }
        // Server sẽ tự xoá tài khoản Keycloak; huỷ luôn phiên trên máy này cho chắc, rồi xoá mọi thứ trên máy (A9).
        endSessionQuietly(fresh.refreshToken)
        userSession.discardDeletedAccount()
        logger.i { "Đã xoá tài khoản" }
        return AppResult.Success(AccountDeletion.Deleted)
    }

    /**
     * Compare-and-set token mới vào phiên **của đúng user đã xác nhận**; trả phiên vừa bị thay (`null` = không thay).
     * So theo user chứ không theo refresh token: sync nền có thể vừa xoay refresh token trong lúc trình duyệt mở, đó vẫn
     * là phiên của user này. Bị xoay tiếp giữa lúc đọc và ghi thì đọc lại rồi thử lần nữa.
     */
    private suspend fun adoptFreshTokens(confirmedFor: StoredSession, fresh: AuthTokens): StoredSession? {
        repeat(ADOPT_ATTEMPTS) {
            val current = sessions.current() ?: return null
            val sameUser = if (confirmedFor.userId != null) {
                current.userId == confirmedFor.userId
            } else {
                current.refreshToken == confirmedFor.refreshToken
            }
            if (!sameUser) return null
            if (tokens.replaceIfCurrent(refreshedFrom = current.refreshToken, tokens = fresh)) return current
        }
        return null
    }

    /**
     * Đổi mật khẩu trong tab riêng tạo một phiên Keycloak mới; phiên app vừa bị thay không còn ai dùng, đóng nó để không
     * nằm lại tới khi hết hạn. Hai token cùng `sid` (trình duyệt không hỗ trợ tab riêng nên vẫn dùng chung phiên SSO) thì
     * đóng cái cũ là đóng luôn cái mới: giữ nguyên. Không đọc được `sid` thì cũng giữ nguyên, không đoán.
     */
    private suspend fun endReplacedSession(replaced: StoredSession, fresh: AuthTokens) {
        val oldSession = TokenClaims.sessionId(replaced.refreshToken) ?: TokenClaims.sessionId(replaced.accessToken)
        val newSession = TokenClaims.sessionId(fresh.refreshToken) ?: TokenClaims.sessionId(fresh.accessToken)
        if (oldSession != null && newSession != null && oldSession != newSession) endSessionQuietly(replaced.refreshToken)
    }

    /** `true` = server đã xoá, `false` = chắc chắn chưa xoá, `null` = vẫn không biết. */
    private suspend fun wasDeleted(): Boolean? = when (val me = remote.profile()) {
        is AppResult.Success -> false
        is AppResult.Failure -> if (me.error.isUserDeleted()) true else null
    }

    private suspend fun endSessionQuietly(refreshToken: String) {
        authenticator.endSession(refreshToken).onFailure { logger.w { "Huỷ phiên phía IdP thất bại: $it" } }
    }

    private companion object {
        const val ADOPT_ATTEMPTS = 3
    }
}
