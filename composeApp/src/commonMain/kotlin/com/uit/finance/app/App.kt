package com.uit.finance.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect
import com.uit.finance.app.navigation.FinanceNavHost
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.reminder.ReminderCoordinator
import org.koin.compose.koinInject

/** Shared root of the UI for Android and iOS. Koin must be started before this composes. */
@Composable
fun App() {
    // Mỗi lần app quay lại foreground: đồng bộ ngay (hoặc thử lại nếu lần trước lỗi).
    val userSession = koinInject<UserSession>()
    val reminderCoordinator = koinInject<ReminderCoordinator>()
    LifecycleStartEffect(userSession, reminderCoordinator) {
        userSession.refresh()
        reminderCoordinator.refresh()
        onStopOrDispose { }
    }
    FinanceTheme {
        FinanceNavHost()
    }
}
