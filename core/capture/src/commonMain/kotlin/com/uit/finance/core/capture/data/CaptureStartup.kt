package com.uit.finance.core.capture.data

import com.uit.finance.core.capture.domain.repository.CaptureRepository
import com.uit.finance.core.capture.domain.repository.CaptureSettingsRepository
import com.uit.finance.core.notification.NotificationAccess
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Mỗi lần sổ của user được mở mà user đã bật capture: nhờ hệ thống nối lại listener (hãng máy hay tắt service, L10)
 * và dọn nguyên văn quá hạn (S2).
 */
internal class CaptureStartup(
    private val session: UserSession,
    private val settings: CaptureSettingsRepository,
    private val repository: CaptureRepository,
    private val access: NotificationAccess,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            combine(session.state, settings.observe()) { state, current -> state is LocalDataState.Ready && current.enabled }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    if (access.isSupported) access.requestRebind()
                    repository.purgeExpired()
                }
        }
    }
}
