package com.uit.finance.feature.auth.testing

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.SignOutNotice
import com.uit.finance.feature.auth.domain.model.UserProfile
import com.uit.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAuthRepository : AuthRepository {
    val sessions = MutableStateFlow<Session?>(null)
    var signInResult: AppResult<SignInResult> = AppResult.Failure(AppError.Unknown("chưa cấu hình"))
    var currentUserResult: AppResult<UserProfile> = AppResult.Failure(AppError.Unknown("chưa cấu hình"))

    val signInCalls = mutableListOf<SignInMode>()

    /** Gán vào thì `signIn` treo tới khi complete — để test thấy được trạng thái đang chờ browser. */
    var signInGate: CompletableDeferred<Unit>? = null

    override suspend fun signIn(mode: SignInMode): AppResult<SignInResult> {
        signInCalls += mode
        signInGate?.await()
        return signInResult
    }

    override fun observeSession(): Flow<Session?> = sessions

    override suspend fun currentUser(): AppResult<UserProfile> = currentUserResult

    var otherAccountsResult: AppResult<Int> = AppResult.Success(0)
    var wipeOtherCalls = 0

    override suspend fun otherAccountsOnDevice(): AppResult<Int> = otherAccountsResult

    val localData = MutableStateFlow(LocalDataStatus.Ready)
    var retryLocalDataCalls = 0

    override fun observeLocalDataStatus(): Flow<LocalDataStatus> = localData

    override fun retryLocalData() {
        retryLocalDataCalls += 1
    }

    override suspend fun wipeOtherAccounts(): AppResult<Unit> {
        wipeOtherCalls += 1
        return AppResult.Success(Unit)
    }

    var localDataWithoutSession = false
    var wipeLocalCalls = 0

    override suspend fun hasLocalDataWithoutSession(): Boolean = localDataWithoutSession

    override suspend fun wipeLocalData(): AppResult<Unit> {
        wipeLocalCalls += 1
        localDataWithoutSession = false
        return AppResult.Success(Unit)
    }

    val notice = MutableStateFlow<SignOutNotice?>(null)
    var acknowledgeCalls = 0

    override fun observeSignOutNotice(): Flow<SignOutNotice?> = notice

    override fun acknowledgeSignOutNotice() {
        acknowledgeCalls += 1
        notice.value = null
    }
}
