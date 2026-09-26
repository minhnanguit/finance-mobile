package com.mosaicglobal.finance.feature.auth.domain.repository

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.SignInMode
import com.mosaicglobal.finance.feature.auth.domain.model.SignInResult
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun signIn(mode: SignInMode): AppResult<SignInResult>

    /** Huỷ session trên IdP (best effort) và LUÔN xoá session local. */
    suspend fun logout(): AppResult<Unit>

    /** `null` khi chưa đăng nhập. Emit ở mọi thay đổi, kể cả khi token rotate. */
    fun observeSession(): Flow<Session?>

    suspend fun currentUser(): AppResult<UserProfile>
}
