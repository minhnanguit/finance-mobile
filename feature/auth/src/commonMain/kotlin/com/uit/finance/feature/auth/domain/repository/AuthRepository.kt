package com.uit.finance.feature.auth.domain.repository

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.SignOutNotice
import com.uit.finance.feature.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun signIn(mode: SignInMode): AppResult<SignInResult>

    /** Số tài khoản khác còn sổ trên máy này (người dùng trước đăng nhập cùng máy). */
    suspend fun otherAccountsOnDevice(): AppResult<Int>

    /** Xoá sổ của mọi tài khoản khác trên máy này. */
    suspend fun wipeOtherAccounts(): AppResult<Unit>

    /**
     * Chưa ai đăng nhập mà máy vẫn còn sổ: phiên cũ hết hạn (B7), hoặc tài khoản đã bị xoá từ máy khác lúc máy này
     * offline (ADR-008 A9 — máy này không còn cách nào biết, vì tài khoản Keycloak đã mất).
     */
    suspend fun hasLocalDataWithoutSession(): Boolean

    /** Xoá mọi sổ (và khoá, cài đặt theo tài khoản) còn trên máy. Không làm gì khi đang có người đăng nhập. */
    suspend fun wipeLocalData(): AppResult<Unit>

    /** `null` khi chưa đăng nhập. Emit ở mọi thay đổi, kể cả khi token rotate. */
    fun observeSession(): Flow<Session?>

    suspend fun currentUser(): AppResult<UserProfile>

    /** Sổ trên máy của phiên hiện tại đã dùng được chưa (S6). */
    fun observeLocalDataStatus(): Flow<LocalDataStatus>

    /** Thử mở lại sổ sau khi lần trước thất bại (ví dụ vừa có mạng lại). */
    fun retryLocalData()

    /** Phiên vừa bị kết thúc vì lý do hệ thống (tài khoản bị xoá); `null` khi không có gì để báo. */
    fun observeSignOutNotice(): Flow<SignOutNotice?>

    fun acknowledgeSignOutNotice()
}
