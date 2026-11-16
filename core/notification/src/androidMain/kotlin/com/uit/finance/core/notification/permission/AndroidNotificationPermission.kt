package com.uit.finance.core.notification.permission

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.uit.finance.core.notification.NotificationPermission
import com.uit.finance.core.notification.NotificationPermissionStatus

internal class AndroidNotificationPermission(private val context: Context) : NotificationPermission {
    override suspend fun status(): NotificationPermissionStatus =
        if (context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        ) NotificationPermissionStatus.Granted else NotificationPermissionStatus.Denied

    override suspend fun request(): NotificationPermissionStatus {
        if (status() == NotificationPermissionStatus.Granted) return NotificationPermissionStatus.Granted
        if (Build.VERSION.SDK_INT < 33) return NotificationPermissionStatus.Denied
        return PermissionRequestBridge.run {
            context.startActivity(
                Intent(context, NotificationPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun openSystemSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
