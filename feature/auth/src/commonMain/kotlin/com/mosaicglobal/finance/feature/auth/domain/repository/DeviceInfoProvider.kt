package com.mosaicglobal.finance.feature.auth.domain.repository

import com.mosaicglobal.finance.feature.auth.domain.model.DeviceInfo

fun interface DeviceInfoProvider {
    suspend fun current(): DeviceInfo
}
