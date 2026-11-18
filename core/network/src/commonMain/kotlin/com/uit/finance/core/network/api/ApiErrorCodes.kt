package com.uit.finance.core.network.api

/**
 * Mã lỗi ổn định trong RFC 7807 (`code`) mà app rẽ nhánh theo. Đặt một chỗ để không gõ lại chuỗi ở
 * nhiều module; giá trị phải khớp contract đang pin (`api/openapi.yaml`).
 */
object ApiErrorCodes {
    /** 403 ở mọi endpoint: tài khoản đã bị xoá (ADR-008 A6). App xoá dữ liệu trên máy. */
    const val USER_DELETED: String = "identity.user_deleted"

    /** 403 ở `DELETE /me`: phải đăng nhập lại ngay trước khi xoá tài khoản (ADR-008 A2). */
    const val REAUTH_REQUIRED: String = "identity.reauth_required"

    /** 409 ở pull: cursor lớn hơn dữ liệu server (server được khôi phục). */
    const val CURSOR_AHEAD: String = "sync.cursor_ahead"

    /** 410 ở pull: server đã dọn tombstone cũ hơn cursor (máy offline quá lâu, ADR-008 T3). */
    const val CURSOR_EXPIRED: String = "sync.cursor_expired"
}
