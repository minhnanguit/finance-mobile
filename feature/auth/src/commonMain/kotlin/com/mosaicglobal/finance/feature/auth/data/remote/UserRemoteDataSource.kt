package com.mosaicglobal.finance.feature.auth.data.remote

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.network.api.UserApi
import com.mosaicglobal.finance.core.network.api.apiCall
import com.mosaicglobal.finance.core.network.api.model.UserProfileDto

/** Bọc `UserApi` (vốn throw) thành `AppResult`. */
internal class UserRemoteDataSource(private val userApi: UserApi) {
    suspend fun currentUser(): AppResult<UserProfileDto> = apiCall { userApi.getCurrentUser() }
}
