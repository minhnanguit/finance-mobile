package com.mosaicglobal.finance.core.network.api

import com.mosaicglobal.finance.core.network.api.model.UserProfileDto

/** `me` tag of the contract. */
interface UserApi {
    suspend fun getCurrentUser(): UserProfileDto
}
