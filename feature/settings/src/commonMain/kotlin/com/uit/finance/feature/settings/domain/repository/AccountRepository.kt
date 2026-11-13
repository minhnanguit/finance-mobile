package com.uit.finance.feature.settings.domain.repository

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.model.AccountProfile
import com.uit.finance.feature.settings.domain.model.PasswordChange
import kotlinx.coroutines.flow.Flow

interface AccountRepository {

    /** Bản từ token đang giữ (nếu đọc được), rồi bản của server (nếu gọi được). Không phát lỗi. */
    fun profile(): Flow<AccountProfile>

    /** Số thay đổi trên máy chưa lên server: đăng xuất hay xoá tài khoản lúc này sẽ mất chúng. */
    suspend fun unsentChangeCount(): Long

    /**
     * Huỷ phiên trên Keycloak (best effort), **xoá sổ trên máy** của user (ADR-006 B5) và LUÔN xoá phiên local.
     */
    suspend fun logout(): AppResult<Unit>

    /**
     * Xoá vĩnh viễn tài khoản (ADR-008): đăng nhập lại trong trình duyệt (`prompt=login`), kiểm đúng tài khoản, gọi
     * `DELETE /me` bằng token vừa xác nhận, rồi xoá mọi thứ trên máy. Lỗi mạng/server trả `Failure`.
     */
    suspend fun deleteAccount(): AppResult<AccountDeletion>

    /**
     * Đổi mật khẩu trên form của Keycloak (ADR-008 A10). Thành công thì phiên trên máy chuyển sang token mới, vì
     * "Đăng xuất khỏi thiết bị khác" có thể đã huỷ phiên cũ. Lỗi mạng/Keycloak trả `Failure`.
     */
    suspend fun changePassword(): AppResult<PasswordChange>
}
