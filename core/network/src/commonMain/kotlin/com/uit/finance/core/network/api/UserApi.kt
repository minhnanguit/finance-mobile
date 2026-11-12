package com.uit.finance.core.network.api

import com.uit.finance.core.network.api.model.UserProfileDto

/**
 * Tag `me` của contract. Implementation throw [ApiException] khi non-2xx; bọc lời gọi bằng [apiCall]
 * để nhận `AppResult`.
 */
interface UserApi {
    suspend fun getCurrentUser(): UserProfileDto

    /**
     * Xoá vĩnh viễn tài khoản (contract 2.3.0, ADR-008). Token phải vừa đăng nhập lại (`auth_time` trong 5
     * phút), nếu không server trả `403 identity.reauth_required`. Thành công = `202`; gọi lại sau khi đã xoá
     * nhận `403 identity.user_deleted`.
     */
    suspend fun deleteCurrentUser()
}
