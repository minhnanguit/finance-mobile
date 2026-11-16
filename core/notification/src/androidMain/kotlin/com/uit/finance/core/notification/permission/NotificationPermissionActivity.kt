package com.uit.finance.core.notification.permission

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import com.uit.finance.core.notification.NotificationPermissionStatus

class NotificationPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 33) {
            finishWith(NotificationPermissionStatus.Granted)
        } else {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            finishWith(
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    NotificationPermissionStatus.Granted
                } else {
                    NotificationPermissionStatus.Denied
                },
            )
        }
    }

    private fun finishWith(status: NotificationPermissionStatus) {
        PermissionRequestBridge.complete(status)
        finish()
    }

    internal companion object { const val REQUEST_CODE = 7112 }
}
