package com.uit.finance.core.common.platform

import android.os.Build

actual fun currentPlatformInfo(): PlatformInfo = PlatformInfo(
    platform = Platform.ANDROID,
    deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .ifBlank { "Android device" }
        .take(MAX_DEVICE_NAME_LENGTH),
    osVersion = "Android ${Build.VERSION.RELEASE}",
)

private const val MAX_DEVICE_NAME_LENGTH = 100
