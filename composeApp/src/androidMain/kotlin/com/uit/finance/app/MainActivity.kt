package com.uit.finance.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.uit.finance.core.recurring.reminder.AndroidNotificationPermissionGate
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val notificationPermission: AndroidNotificationPermissionGate by inject()
    private var pendingPermissionResult: ((Boolean) -> Unit)? = null

    // Quyền thông báo (Android 13+) chỉ xin được từ Activity: gắn trình xin quyền cho core/recurring dùng khi user bật nhắc.
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermissionResult?.invoke(granted)
        pendingPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        notificationPermission.launcher = { onResult ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pendingPermissionResult = onResult
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult(true)
            }
        }
        setContent { App() }
    }

    override fun onDestroy() {
        notificationPermission.launcher = null
        super.onDestroy()
    }
}
