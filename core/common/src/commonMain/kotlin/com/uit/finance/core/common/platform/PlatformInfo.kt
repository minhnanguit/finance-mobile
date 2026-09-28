package com.uit.finance.core.common.platform

enum class Platform { ANDROID, IOS }

data class PlatformInfo(
    val platform: Platform,
    /** Human readable device model, e.g. "Pixel 9" or "iPhone". */
    val deviceName: String,
    val osVersion: String,
)

expect fun currentPlatformInfo(): PlatformInfo
