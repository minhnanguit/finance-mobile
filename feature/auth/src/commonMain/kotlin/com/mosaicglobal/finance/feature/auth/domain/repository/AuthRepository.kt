package com.mosaicglobal.finance.feature.auth.domain.repository

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.Credentials
import com.mosaicglobal.finance.feature.auth.domain.model.Registration
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun register(registration: Registration): AppResult<Session>
    suspend fun login(credentials: Credentials): AppResult<Session>
    /** Revokes the device session remotely (best effort) and always clears the local session. */
    suspend fun logout(): AppResult<Unit>
    /** `null` while signed out. Emits on every change, including token rotation. */
    fun observeSession(): Flow<Session?>
    suspend fun currentUser(): AppResult<UserProfile>
}
