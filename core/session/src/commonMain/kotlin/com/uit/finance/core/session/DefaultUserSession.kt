package com.uit.finance.core.session

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.UserDatabases
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.datastore.session.StoredSession
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.apiCall
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
    private val scope: CoroutineScope,
    private val logger: Logger,
) : UserSession {

    /** Chỉ hai thứ quyết định DB nào mở: có phiên hay không, và `userId` là gì. Token rotate thì bỏ qua. */
    private data class Identity(val signedIn: Boolean, val userId: String?)

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
        scheduler.cancelAll()
        databases.delete(userId)
        mutableState.value = LocalDataState.SignedOut
    }

    override suspend fun otherUsersOnDevice(currentUserId: String): Set<String> =
        databases.storedUserIds() - currentUserId

    override suspend fun wipe(userIds: Set<String>) {
        userIds.forEach { databases.delete(it) }
    }

    private suspend fun onIdentity(identity: Identity) {
        when {
            !identity.signedIn -> {
                // Hết phiên hoặc đã đăng xuất: chỉ đóng, KHÔNG xoá (B7). Xoá chỉ đi qua wipeCurrentUser.
                scheduler.cancelAll()
                databases.close()
                mutableState.value = LocalDataState.SignedOut
            }
            identity.userId == null -> resolveUserId()
            else -> openFor(identity.userId)
        }
    }

    /** Lấy `userId` từ `/me` rồi gắn vào phiên; lần phát kế tiếp của phiên sẽ mở DB. */
    private suspend fun resolveUserId() {
        mutableState.value = LocalDataState.ResolvingUser
        when (val profile = apiCall { userApi.getCurrentUser() }) {
            is AppResult.Success -> sessions.attachUserId(profile.value.id)
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

    private fun StoredSession?.identity() = Identity(signedIn = this != null, userId = this?.userId)
}
