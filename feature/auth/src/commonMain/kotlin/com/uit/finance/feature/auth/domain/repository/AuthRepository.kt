package com.uit.finance.feature.auth.domain.repository

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun signIn(mode: SignInMode): AppResult<SignInResult>

    /**
     * Huỷ session trên IdP (best effort), **xoá sổ cục bộ của user** (ADR-006 B5) và LUÔN xoá session
     * local. Thay đổi chưa gửi sẽ mất: hỏi user trước bằng [unsentChangeCount].
     */
    suspend fun logout(): AppResult<Unit>

    /** Số thay đổi ghi ở máy mà chưa lên server. */
    suspend fun unsentChangeCount(): Long

    /** Số tài khoản khác còn sổ trên máy này (người dùng trước đăng nhập cùng máy). */
    suspend fun otherAccountsOnDevice(): AppResult<Int>

    /** Xoá sổ của mọi tài khoản khác trên máy này. */
    suspend fun wipeOtherAccounts(): AppResult<Unit>

    /** `null` khi chưa đăng nhập. Emit ở mọi thay đổi, kể cả khi token rotate. */
    fun observeSession(): Flow<Session?>

    suspend fun currentUser(): AppResult<UserProfile>
}
