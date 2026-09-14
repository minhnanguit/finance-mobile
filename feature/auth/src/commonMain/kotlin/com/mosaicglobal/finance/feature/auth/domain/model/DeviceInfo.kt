package com.mosaicglobal.finance.feature.auth.domain.model

import com.mosaicglobal.finance.core.common.platform.Platform

/** Sessions are bound to a device so they can be revoked individually. */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
)
