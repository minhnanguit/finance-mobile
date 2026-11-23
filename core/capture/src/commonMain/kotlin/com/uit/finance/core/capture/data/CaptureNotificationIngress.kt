@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.data

import com.uit.finance.core.capture.data.settings.CaptureSettingsRepositoryImpl
import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.CaptureEnvironment
import com.uit.finance.core.common.text.foldForSearch
import com.uit.finance.core.notification.NotificationIngress
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Cầu nối listener (core/notification) → [CaptureIngestor]. Hai bước lọc chỉ đọc key cấp máy, không cần sổ mở (L9);
 * việc nặng chạy trên scope ứng dụng inject qua Koin (L6), không chặn listener.
 */
internal class CaptureNotificationIngress(
    private val settings: CaptureSettingsRepositoryImpl,
    private val ingestor: CaptureIngestor,
    private val environment: CaptureEnvironment,
    private val scope: CoroutineScope,
) : NotificationIngress {

    override fun accepts(packageName: String): Boolean {
        if (!environment.debug && packageName in BankId.DEMO.packages) return false
        return packageName in settings.devicePackages()
    }

    override fun acceptsSender(packageName: String, title: String?): Boolean {
        if (packageName !in BankId.SMS_APP_PACKAGES) return true
        val sender = title?.foldForSearch()?.uppercase() ?: return false
        return settings.deviceSmsSenders().any { sender == it || sender.startsWith("$it ") }
    }

    override fun onPosted(packageName: String, title: String?, text: String, postedAt: Instant) {
        scope.launch { ingestor.ingestNotification(packageName, title, text, postedAt) }
    }
}
