package com.mosaicglobal.finance.core.network.api

import com.mosaicglobal.finance.core.network.api.model.LoginRequestDto
import com.mosaicglobal.finance.core.network.api.model.LogoutRequestDto
import com.mosaicglobal.finance.core.network.api.model.RefreshRequestDto
import com.mosaicglobal.finance.core.network.api.model.RegisterRequestDto
import com.mosaicglobal.finance.core.network.api.model.TokenPairDto

/**
 * `auth` tag of the contract. Implementations throw [ApiException] on non-2xx responses and
 * network exceptions on transport failures; wrap calls in [apiCall] to obtain an `AppResult`.
 */
interface AuthApi {
    suspend fun register(request: RegisterRequestDto): TokenPairDto
    suspend fun login(request: LoginRequestDto): TokenPairDto
    suspend fun refresh(request: RefreshRequestDto): TokenPairDto
    suspend fun logout(request: LogoutRequestDto)
}
