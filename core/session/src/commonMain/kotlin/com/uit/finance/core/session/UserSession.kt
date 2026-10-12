package com.uit.finance.core.session

import com.uit.finance.core.common.result.AppError
import kotlinx.coroutines.flow.StateFlow

/** Dữ liệu cục bộ của phiên đăng nhập đang ở trạng thái nào. */
sealed interface LocalDataState {
    /** Chưa đăng nhập: không có DB nào mở. */
    data object SignedOut : LocalDataState

    /** Có token nhưng chưa biết `userId` (đang gọi `/me`). */
    data object ResolvingUser : LocalDataState

    /** Sổ của [userId] đã mở, sync đã được hẹn. */
    data class Ready(val userId: String) : LocalDataState

    /** Chưa lấy được `userId` (lần đầu mà mất mạng, S6) hoặc không mở được DB. Thử lại bằng [UserSession.refresh]. */
    data class Failed(val error: AppError) : LocalDataState
}

/**
 * Vòng đời dữ liệu cục bộ theo phiên đăng nhập (ADR-006 B5, B7). Nơi duy nhất mở, đóng, xoá DB của user:
 *
 * | Sự kiện | Việc |
 * |---|---|
 * | Có token, biết `userId` | Mở `finance-<userId>.db`, hẹn sync |
 * | Hết phiên (refresh token bị từ chối, offline > 30 ngày) | **Chỉ đóng** DB, giữ file: đăng nhập lại đúng user thì gửi tiếp (B7) |
 * | User bấm đăng xuất | [wipeCurrentUser]: xoá file + khoá (B5) |
 * | User khác đăng nhập vào máy còn sổ người cũ | Hỏi rồi [wipe] (B5) |
 */
interface UserSession {
    val state: StateFlow<LocalDataState>

    /** Bắt đầu theo dõi phiên. Gọi đúng một lần lúc app khởi động. */
    fun start()

    /** App quay lại foreground: thử lại nếu đang lỗi, còn không thì đồng bộ ngay. */
    fun refresh()

    /** Đăng xuất: dừng sync, xoá DB và khoá của user hiện tại. Gọi **trước** khi xoá token. */
    suspend fun wipeCurrentUser()

    /** User khác [currentUserId] còn sổ trên máy này. */
    suspend fun otherUsersOnDevice(currentUserId: String): Set<String>

    suspend fun wipe(userIds: Set<String>)
}
