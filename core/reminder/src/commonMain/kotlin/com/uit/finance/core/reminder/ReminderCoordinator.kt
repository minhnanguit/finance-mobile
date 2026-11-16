package com.uit.finance.core.reminder

interface ReminderCoordinator {
    fun start()
    fun refresh()
}

object ReminderNotifications {
    const val GROUP = "reminder.daily"
    const val ACTION_ADD_TRANSACTION = "reminder.add_transaction"
}
