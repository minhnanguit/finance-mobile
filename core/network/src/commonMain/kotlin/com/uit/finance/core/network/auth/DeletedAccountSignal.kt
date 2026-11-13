package com.uit.finance.core.network.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Server trả `403 identity.user_deleted` cho một request (ADR-008 A9): tài khoản của token đó đã bị xoá,
 * có thể từ máy khác. Network layer phát, `core/session` nghe để xoá dữ liệu trên máy.
 *
 * Phát kèm access token của request bị từ chối, để bên nghe chỉ xử lý khi đó vẫn là phiên hiện tại: một
 * request của người cũ về muộn không được xoá sổ của người vừa đăng nhập (ADR-006 B5). Không giữ trạng
 * thái, không phụ thuộc gì: HttpClient và `UserSession` cùng dùng mà không tạo vòng phụ thuộc trong Koin.
 */
class DeletedAccountSignal {

    private val events = MutableSharedFlow<String>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Access token của các request bị từ chối vì tài khoản đã xoá. */
    val rejectedAccessTokens: SharedFlow<String> = events.asSharedFlow()

    fun report(accessToken: String) {
        events.tryEmit(accessToken)
    }

    private companion object {
        const val BUFFER = 8
    }
}
