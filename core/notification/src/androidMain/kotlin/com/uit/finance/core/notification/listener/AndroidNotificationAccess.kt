package com.uit.finance.core.notification.listener

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.uit.finance.core.notification.NotificationAccess

internal class AndroidNotificationAccess(private val context: Context) : NotificationAccess {

    private val component = ComponentName(context, BankNotificationListener::class.java)

    override val isSupported: Boolean = true

    override fun isGranted(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS) ?: return false
        return enabled.split(':').mapNotNull(ComponentName::unflattenFromString).any { it == component }
    }

    /**
     * Android 11+ mở thẳng trang của app; máy cũ mở danh sách. APK cài ngoài Play Store trên Android 13+ có thể bị
     * chặn ("cài đặt bị hạn chế"): màn giải thích hướng dẫn user mở Thông tin ứng dụng → ⋮ → Cho phép.
     */
    override fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        val launch = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun requestRebind() {
        if (isGranted()) NotificationListenerService.requestRebind(component)
    }

    private companion object {
        const val ENABLED_LISTENERS = "enabled_notification_listeners"
    }
}
