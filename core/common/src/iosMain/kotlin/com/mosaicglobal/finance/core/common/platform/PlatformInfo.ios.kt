package com.mosaicglobal.finance.core.common.platform

import platform.UIKit.UIDevice

actual fun currentPlatformInfo(): PlatformInfo {
    val device = UIDevice.currentDevice
    return PlatformInfo(
        platform = Platform.IOS,
        deviceName = device.name.ifBlank { device.model }.take(MAX_DEVICE_NAME_LENGTH),
        osVersion = "${device.systemName} ${device.systemVersion}",
    )
}

private const val MAX_DEVICE_NAME_LENGTH = 100
