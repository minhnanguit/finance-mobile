package com.uit.finance.core.notification.tap

import android.content.Intent
import com.uit.finance.core.notification.DefaultNotificationTaps
import com.uit.finance.core.notification.NotificationTaps

const val EXTRA_NOTIFICATION_ACTION = "com.uit.finance.notification.ACTION"

fun handleNotificationIntent(intent: Intent?, taps: NotificationTaps) {
    val action = intent?.getStringExtra(EXTRA_NOTIFICATION_ACTION) ?: return
    (taps as? DefaultNotificationTaps)?.onTapped(action)
    intent.removeExtra(EXTRA_NOTIFICATION_ACTION)
}
