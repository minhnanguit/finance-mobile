package com.uit.finance.core.notification.listener

import com.uit.finance.core.notification.NotificationAccess

/** iOS không cho app đọc thông báo của app khác (plan I1): chỉ có màn dán tin nhắn. */
internal class IosNotificationAccess : NotificationAccess {
    override val isSupported: Boolean = false
    override fun isGranted(): Boolean = false
    override fun openSettings() = Unit
    override fun requestRebind() = Unit
}
