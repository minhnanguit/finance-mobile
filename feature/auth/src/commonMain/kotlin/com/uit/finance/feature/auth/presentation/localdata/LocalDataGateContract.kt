package com.uit.finance.feature.auth.presentation.localdata

import com.uit.finance.feature.auth.domain.model.LocalDataStatus

data class LocalDataGateState(
    val status: LocalDataStatus = LocalDataStatus.Preparing,
    /** Vừa bấm thử lại, đang chờ kết quả. */
    val isRetrying: Boolean = false,
)

sealed interface LocalDataGateIntent {
    data object RetryClicked : LocalDataGateIntent
    data object LogoutClicked : LocalDataGateIntent
}

sealed interface LocalDataGateEffect {
    /** Sổ đã mở: vào app. */
    data object Ready : LocalDataGateEffect
    data object OpenProfile : LocalDataGateEffect
}
