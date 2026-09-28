package com.uit.finance.feature.auth.data.remote

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.apiCall
import com.uit.finance.core.network.api.model.UserProfileDto

/** Bọc `UserApi` (vốn throw) thành `AppResult`. */
internal class UserRemoteDataSource(private val userApi: UserApi) {
    suspend fun currentUser(): AppResult<UserProfileDto> = apiCall { userApi.getCurrentUser() }
}
