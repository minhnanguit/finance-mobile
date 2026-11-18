package com.uit.finance.feature.settings.data.remote

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.apiCall
import com.uit.finance.core.network.api.model.UserProfileDto

/** Bọc `UserApi` (vốn throw) thành `AppResult`. */
internal class AccountRemoteDataSource(private val userApi: UserApi) {
    suspend fun profile(): AppResult<UserProfileDto> = apiCall { userApi.getCurrentUser() }

    suspend fun deleteAccount(): AppResult<Unit> = apiCall { userApi.deleteCurrentUser() }
}
