package com.uit.finance.feature.auth.domain.repository

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun signIn(mode: SignInMode): AppResult<SignInResult>

    /** Huỷ session trên IdP (best effort) và LUÔN xoá session local. */
    suspend fun logout(): AppResult<Unit>

    /** `null` khi chưa đăng nhập. Emit ở mọi thay đổi, kể cả khi token rotate. */
    fun observeSession(): Flow<Session?>

    suspend fun currentUser(): AppResult<UserProfile>
}
