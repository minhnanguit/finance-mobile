package com.uit.finance.core.session

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.UserDatabases
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.datastore.session.StoredSession
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.apiCall
import com.uit.finance.core.sync.engine.SyncEngine
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class DefaultUserSession(
    private val sessions: SessionStore,
    private val userApi: UserApi,
    private val databases: UserDatabases,
    private val scheduler: SyncScheduler,
    private val syncEngine: SyncEngine,
    private val scope: CoroutineScope,
    private val logger: Logger,
) : UserSession {

    /**
     * Thứ quyết định DB nào được mở. Token rotate thì bỏ qua, trừ khi chưa biết `userId`: lúc đó mỗi lần
     * đăng nhập là một danh tính khác nhau (refresh token khác), kể cả khi `StateFlow` gộp mất bước đăng
     * xuất ở giữa, để việc gọi `/me` của phiên cũ luôn bị huỷ.
     */
    private data class Identity(val signedIn: Boolean, val userId: String?, val pendingLogin: String?)

    private val mutableState = MutableStateFlow<LocalDataState>(LocalDataState.SignedOut)
    override val state: StateFlow<LocalDataState> = mutableState.asStateFlow()

    private var started = false

    override fun start() {
        if (started) return
        started = true
        scope.launch {
            sessions.session
                .map { it.identity() }
                .distinctUntilChanged()
                // Phiên đổi giữa chừng (đăng xuất lúc đang gọi /me) thì huỷ việc cũ.
                .collectLatest { onIdentity(it) }
        }
    }

    override fun refresh() {
        scope.launch {
            when (state.value) {
                is LocalDataState.Failed -> onIdentity(sessions.current().identity())
                is LocalDataState.Ready -> scheduler.requestImmediate()
                LocalDataState.ResolvingUser, LocalDataState.SignedOut -> Unit
            }
        }
    }

    override suspend fun wipeCurrentUser() {
        val userId = databases.activeUserId ?: sessions.current()?.userId ?: return
        stopSync()
        databases.delete(userId)
        mutableState.value = LocalDataState.SignedOut
    }

    override suspend fun otherUsersOnDevice(currentUserId: String): Set<String> =
        databases.storedUserIds() - currentUserId

    override suspend fun wipe(userIds: Set<String>) {
        userIds.forEach { databases.delete(it) }
    }

    private suspend fun onIdentity(identity: Identity) {
        // Đổi người (hoặc chưa biết là ai) thì tháo hẳn DB đang mở trước: không vòng sync nào được đọc sổ của
        // người cũ rồi gửi đi bằng token của người mới (B5). Chỉ đóng, KHÔNG xoá (B7); xoá đi qua wipeCurrentUser.
        if (!identity.signedIn || databases.activeUserId != identity.userId) detach()
        when {
            !identity.signedIn -> mutableState.value = LocalDataState.SignedOut
            identity.userId == null -> resolveUserId()
            else -> openFor(identity.userId)
        }
    }

    private suspend fun detach() {
        stopSync()
        databases.close()
    }

    /** Huỷ lịch nền **và** vòng sync đang chạy dở, chờ nó dừng hẳn rồi mới đụng tới DB. */
    private suspend fun stopSync() {
        scheduler.cancelAll()
        syncEngine.stop()
    }

    /** Lấy `userId` từ `/me` rồi gắn vào **đúng** phiên đã hỏi; lần phát kế tiếp của phiên sẽ mở DB. */
    private suspend fun resolveUserId() {
        mutableState.value = LocalDataState.ResolvingUser
        val askedFor = sessions.current() ?: return
        when (val profile = apiCall { userApi.getCurrentUser() }) {
            is AppResult.Success ->
                if (!sessions.attachUserId(profile.value.id, forRefreshToken = askedFor.refreshToken)) {
                    logger.i { "Phiên đổi trong lúc gọi /me; bỏ kết quả của phiên cũ" }
                }
            is AppResult.Failure -> {
                logger.w { "Chưa lấy được userId của phiên" }
                mutableState.value = LocalDataState.Failed(profile.error)
            }
        }
    }

    private suspend fun openFor(userId: String) {
        try {
            databases.open(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e { "Không mở được sổ cục bộ: ${e::class.simpleName}" }
            mutableState.value = LocalDataState.Failed(AppError.Storage(e::class.simpleName))
            return
        }
        mutableState.value = LocalDataState.Ready(userId)
        scheduler.schedulePeriodic()
        scheduler.requestImmediate()
    }

    private fun StoredSession?.identity() = Identity(
        signedIn = this != null,
        userId = this?.userId,
        pendingLogin = this?.takeIf { it.userId == null }?.refreshToken,
    )
}
