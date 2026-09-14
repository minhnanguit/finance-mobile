package com.mosaicglobal.finance.feature.auth.data.device

import com.mosaicglobal.finance.core.common.platform.currentPlatformInfo
import com.mosaicglobal.finance.core.datastore.settings.AppSettings
import com.mosaicglobal.finance.feature.auth.domain.model.DeviceInfo
import com.mosaicglobal.finance.feature.auth.domain.repository.DeviceInfoProvider

internal class DefaultDeviceInfoProvider(private val appSettings: AppSettings) : DeviceInfoProvider {
    override suspend fun current(): DeviceInfo {
        val info = currentPlatformInfo()
        return DeviceInfo(
            deviceId = appSettings.installationId,
            deviceName = info.deviceName,
            platform = info.platform,
        )
    }
}
