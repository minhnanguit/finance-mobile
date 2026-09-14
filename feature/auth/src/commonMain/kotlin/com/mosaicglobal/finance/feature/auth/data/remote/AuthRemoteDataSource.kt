package com.mosaicglobal.finance.feature.auth.data.remote

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.network.api.AuthApi
import com.mosaicglobal.finance.core.network.api.UserApi
import com.mosaicglobal.finance.core.network.api.apiCall
import com.mosaicglobal.finance.core.network.api.model.DeviceInfoDto
import com.mosaicglobal.finance.core.network.api.model.LoginRequestDto
import com.mosaicglobal.finance.core.network.api.model.LogoutRequestDto
import com.mosaicglobal.finance.core.network.api.model.RegisterRequestDto
import com.mosaicglobal.finance.core.network.api.model.TokenPairDto
import com.mosaicglobal.finance.core.network.api.model.UserProfileDto

/** Thin wrapper that turns the throwing `AuthApi`/`UserApi` into `AppResult`s. */
internal class AuthRemoteDataSource(
    private val authApi: AuthApi,
    private val userApi: UserApi,
) {
    suspend fun register(email: String, password: String, displayName: String, device: DeviceInfoDto): AppResult<TokenPairDto> =
        apiCall { authApi.register(RegisterRequestDto(email, password, displayName, device)) }

    suspend fun login(email: String, password: String, device: DeviceInfoDto): AppResult<TokenPairDto> =
        apiCall { authApi.login(LoginRequestDto(email, password, device)) }

    suspend fun logout(refreshToken: String): AppResult<Unit> =
        apiCall { authApi.logout(LogoutRequestDto(refreshToken)) }

    suspend fun currentUser(): AppResult<UserProfileDto> =
        apiCall { userApi.getCurrentUser() }
}
