package com.uit.finance.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NotificationTaps {
    val pending: StateFlow<String?>
    fun consume()
}

internal class DefaultNotificationTaps : NotificationTaps {
    private val mutablePending = MutableStateFlow<String?>(null)
    override val pending: StateFlow<String?> = mutablePending.asStateFlow()

    fun onTapped(action: String) {
        mutablePending.value = action
    }

    override fun consume() {
        mutablePending.value = null
    }
}
