package com.uit.finance.core.reminder

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.common.time.observeToday
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.notification.LocalNotification
import com.uit.finance.core.notification.LocalNotificationScheduler
import com.uit.finance.core.reminder.domain.ReminderPlanner
import com.uit.finance.core.reminder.domain.model.PlannedReminder
import com.uit.finance.core.reminder.domain.repository.ReminderSettingsRepository
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultReminderCoordinator(
    private val session: UserSession,
    private val settings: ReminderSettingsRepository,
    private val transactions: TransactionRepository,
    private val scheduler: LocalNotificationScheduler,
    private val planner: ReminderPlanner,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val logger: Logger,
) : ReminderCoordinator {
    private val refreshTicks = MutableStateFlow(0L)
    private var started = false

    override fun start() {
        if (started) return
        started = true
        scope.launch {
            session.state
                .map { (it as? LocalDataState.Ready)?.userId }
                .distinctUntilChanged()
                .flatMapLatest { userId -> if (userId == null) flowOf(emptyList()) else plans() }
                .distinctUntilChanged()
                .collect { plan ->
                    scheduler.replaceGroup(ReminderNotifications.GROUP, plan.map(::toNotification))
                    logger.i { "Đã hẹn ${plan.size} lời nhắc ghi chép" }
                }
        }
    }

    override fun refresh() {
        refreshTicks.value += 1
        scope.launch { scheduler.clearDelivered(ReminderNotifications.GROUP) }
    }

    private fun plans() = refreshTicks.flatMapLatest { observeToday(clock) }.flatMapLatest { today ->
        combine(
            settings.observe(),
            transactions.observeFilteredTotals(TransactionFilter(from = today, to = today)).map { it.count > 0 },
        ) { reminderSettings, loggedToday ->
            planner.plan(reminderSettings, clock.now(), TimeZone.currentSystemDefault(), loggedToday)
        }
    }

    private fun toNotification(planned: PlannedReminder) = LocalNotification(
        id = "${ReminderNotifications.GROUP}.${planned.date}",
        group = ReminderNotifications.GROUP,
        fireAt = planned.fireAt,
        title = "Ghi chép hôm nay",
        body = "Hôm nay bạn đã ghi chi tiêu chưa? Chỉ mất vài giây thôi.",
        action = ReminderNotifications.ACTION_ADD_TRANSACTION,
    )
}
