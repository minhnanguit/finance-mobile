package com.uit.finance.feature.settings.testing

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.model.AccountProfile
import com.uit.finance.feature.settings.domain.model.PasswordChange
import com.uit.finance.feature.settings.domain.repository.AccountRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

internal class FakeAccountRepository : AccountRepository {
    val profile = MutableStateFlow<AccountProfile?>(null)
    var unsent = 0L
    var logoutResult: AppResult<Unit> = AppResult.Success(Unit)
    var logoutCalls = 0
    var deletionResult: AppResult<AccountDeletion> = AppResult.Success(AccountDeletion.Deleted)
    var deleteCalls = 0
    var passwordChangeResult: AppResult<PasswordChange> = AppResult.Success(PasswordChange.Changed)
    var passwordChangeCalls = 0

    /** Gán vào thì `deleteAccount` treo tới khi complete: để test thấy trạng thái đang chờ trình duyệt. */
    var deletionGate: CompletableDeferred<Unit>? = null

    /** Gán vào thì `changePassword` treo tới khi complete: form đổi mật khẩu đang mở. */
    var passwordChangeGate: CompletableDeferred<Unit>? = null

    override fun profile(): Flow<AccountProfile> = profile.filterNotNull()
    override suspend fun unsentChangeCount(): Long = unsent
    override suspend fun logout(): AppResult<Unit> {
        logoutCalls++
        return logoutResult
    }
    override suspend fun deleteAccount(): AppResult<AccountDeletion> {
        deleteCalls++
        deletionGate?.await()
        return deletionResult
    }
    override suspend fun changePassword(): AppResult<PasswordChange> {
        passwordChangeCalls++
        passwordChangeGate?.await()
        return passwordChangeResult
    }
}
