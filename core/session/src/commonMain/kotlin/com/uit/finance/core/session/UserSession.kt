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

/** Lý do phiên vừa kết thúc mà người dùng không tự bấm, để màn đăng nhập báo một lần. */
enum class SessionNotice {
    /** Tài khoản đã bị xoá (trên máy này hoặc máy khác), dữ liệu trên máy đã được xoá theo (ADR-008 A9). */
    AccountDeleted,
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
 * | Tài khoản bị xoá (máy này xoá, hoặc server trả `403 identity.user_deleted`) | [discardDeletedAccount]: xoá sổ + token, báo [SessionNotice.AccountDeleted] (ADR-008 A9) |
 */
interface UserSession {
    val state: StateFlow<LocalDataState>

    /** Bắt đầu theo dõi phiên. Gọi đúng một lần lúc app khởi động. */
    fun start()

    /** App quay lại foreground: thử lại nếu đang lỗi, còn không thì đồng bộ ngay. */
    fun refresh()

    /** Thông báo cho màn đăng nhập sau khi phiên bị kết thúc vì một lý do hệ thống; `null` khi không có gì. */
    val notice: StateFlow<SessionNotice?>

    /** Màn hình đã hiện [notice]: không hiện lại nữa. */
    fun acknowledgeNotice()

    /**
     * Đăng xuất: dừng sync, xoá DB, khoá và dữ liệu theo tài khoản ([UserDataCleaner]) của user hiện tại. Gọi
     * **trước** khi xoá token.
     */
    suspend fun wipeCurrentUser()

    /**
     * Tài khoản hiện tại đã bị xoá: dừng sync, xoá sổ + khoá + dữ liệu theo tài khoản, xoá token, rồi đặt
     * [notice] = [SessionNotice.AccountDeleted]. Không cần mạng và gọi nhiều lần không sao (ADR-008 A9).
     */
    suspend fun discardDeletedAccount()

    /** User khác [currentUserId] còn sổ trên máy này. */
    suspend fun otherUsersOnDevice(currentUserId: String): Set<String>

    /**
     * Mọi user còn sổ trên máy, kể cả khi chưa ai đăng nhập: hết phiên thì sổ được giữ (B7) — kể cả khi lý do là tài
     * khoản đã bị xoá từ máy khác mà máy này không kịp nhận `user_deleted` (ADR-008 A9).
     */
    suspend fun usersOnDevice(): Set<String>

    suspend fun wipe(userIds: Set<String>)
}
