package com.mosaicglobal.finance.core.network.api

import com.mosaicglobal.finance.core.network.api.model.UserProfileDto

/**
 * Tag `me` của contract. Implementation throw [ApiException] khi non-2xx; bọc lời gọi bằng [apiCall]
 * để nhận `AppResult`.
 */
interface UserApi {
    suspend fun getCurrentUser(): UserProfileDto
}
