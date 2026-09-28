package com.uit.finance.core.auth.launcher

import kotlinx.coroutines.CompletableDeferred

/**
 * Nối coroutine đang chờ login với Activity nhận redirect. Mỗi lúc chỉ một flow: flow mới thay flow
 * cũ (flow cũ nhận Cancelled).
 *
 * Giới hạn đã biết: nếu process bị kill khi user đang ở browser, coroutine chờ cũng mất theo; redirect
 * quay về sẽ bị bỏ qua và user bấm đăng nhập lại.
 */
internal object AuthorizationFlowBridge {

    private val lock = Any()
    private var pending: CompletableDeferred<LaunchResult>? = null

    val isPending: Boolean get() = synchronized(lock) { pending != null }

    suspend fun run(start: () -> Unit): LaunchResult {
        val deferred = CompletableDeferred<LaunchResult>()
        synchronized(lock) {
            pending?.complete(LaunchResult.Cancelled)
            pending = deferred
        }
        return try {
            start()
            deferred.await()
        } finally {
            synchronized(lock) { if (pending === deferred) pending = null }
        }
    }

    fun complete(result: LaunchResult) {
        synchronized(lock) {
            pending?.complete(result)
            pending = null
        }
    }
}
