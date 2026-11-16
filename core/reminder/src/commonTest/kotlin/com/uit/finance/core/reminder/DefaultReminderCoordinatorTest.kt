@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.uit.finance.core.reminder

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.notification.LocalNotification
import com.uit.finance.core.notification.LocalNotificationScheduler
import com.uit.finance.core.reminder.domain.ReminderPlanner
import com.uit.finance.core.reminder.domain.model.ReminderSettings
import com.uit.finance.core.reminder.domain.repository.ReminderSettingsRepository
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.SessionNotice
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

class DefaultReminderCoordinatorTest {
    private val zone = TimeZone.currentSystemDefault()
    private val day = LocalDate(2026, 11, 16)

    @Test
    fun `signed out schedules an empty daily group`() = runTest {
        val scheduler = FakeScheduler()
        start(scheduler = scheduler)
        runCurrent()
        assertEquals(ReminderNotifications.GROUP to emptyList(), scheduler.groups.single())
    }

    @Test
    fun `enabled day with no transaction schedules fourteen reminders`() = runTest {
        val scheduler = FakeScheduler()
        val session = session(LocalDataState.Ready("user-1"))
        val settings = FakeReminderSettings()
        settings.setEnabled(true)
        start(scheduler = scheduler, session = session, settings = settings, clock = morning())
        runCurrent()

        val scheduled = scheduler.groups.single().second
        val dates = List(ReminderPlanner.WINDOW_DAYS) { day.plus(it, DateTimeUnit.DAY) }
        assertEquals(dates.map { "${ReminderNotifications.GROUP}.$it" }, scheduled.map { it.id })
        assertEquals("Ghi chép hôm nay", scheduled.first().title)
        assertEquals("Hôm nay bạn đã ghi chi tiêu chưa? Chỉ mất vài giây thôi.", scheduled.first().body)
        assertEquals(ReminderNotifications.ACTION_ADD_TRANSACTION, scheduled.first().action)
        assertEquals(at(21, 0), scheduled.first().fireAt)
    }

    @Test
    fun `recording a transaction today removes that day and deleting it restores it`() = runTest {
        val scheduler = FakeScheduler()
        val transactions = FakeTransactionRepository()
        val session = session(LocalDataState.Ready("user-1"))
        val settings = FakeReminderSettings().also { it.setEnabled(true) }
        start(
            scheduler = scheduler,
            session = session,
            settings = settings,
            transactions = transactions,
            clock = morning(),
        )
        runCurrent()

        transactions.transactions.value = listOf(transaction(day))
        runCurrent()
        assertEquals(day.plus(1, DateTimeUnit.DAY).toString(), scheduler.groups.last().second.first().id.substringAfterLast('.'))
        assertEquals(13, scheduler.groups.last().second.size)

        transactions.transactions.value = emptyList()
        runCurrent()
        assertEquals("$day", scheduler.groups.last().second.first().id.substringAfterLast('.'))
        assertEquals(14, scheduler.groups.last().second.size)
    }

    @Test
    fun `changing the time updates fireAt`() = runTest {
        val scheduler = FakeScheduler()
        val session = session(LocalDataState.Ready("user-1"))
        val settings = FakeReminderSettings().also { it.setEnabled(true) }
        start(scheduler = scheduler, session = session, settings = settings, clock = morning())
        runCurrent()

        settings.setTime(LocalTime(22, 30))
        runCurrent()
        assertEquals(at(22, 30), scheduler.groups.last().second.first().fireAt)
    }

    @Test
    fun `signing out clears the scheduled reminders`() = runTest {
        val scheduler = FakeScheduler()
        val session = session(LocalDataState.Ready("user-1"))
        val settings = FakeReminderSettings().also { it.setEnabled(true) }
        start(scheduler = scheduler, session = session, settings = settings, clock = morning())
        runCurrent()

        session.state.value = LocalDataState.SignedOut
        runCurrent()
        assertTrue(scheduler.groups.last().second.isEmpty())
    }

    @Test
    fun `an unchanged plan does not schedule twice`() = runTest {
        val scheduler = FakeScheduler()
        val session = session(LocalDataState.Ready("user-1"))
        val settings = FakeReminderSettings().also { it.setEnabled(true) }
        val coordinator = start(scheduler = scheduler, session = session, settings = settings, clock = morning())
        runCurrent()
        val scheduled = scheduler.groups.size

        coordinator.refresh()
        runCurrent()
        assertEquals(scheduled, scheduler.groups.size)
        assertEquals(1, scheduler.cleared)
    }

    @Test
    fun `refresh after the clock moves slides the window by one day`() = runTest {
        val scheduler = FakeScheduler()
        val clock = morning()
        val session = session(LocalDataState.Ready("user-1"))
        val settings = FakeReminderSettings().also { it.setEnabled(true) }
        val coordinator = start(scheduler = scheduler, session = session, settings = settings, clock = clock)
        runCurrent()

        val next = day.plus(1, DateTimeUnit.DAY)
        clock.set(LocalDateTime(next, LocalTime(10, 0)).toInstant(zone))
        coordinator.refresh()
        runCurrent()
        val scheduled = scheduler.groups.last().second
        assertEquals("${ReminderNotifications.GROUP}.$next", scheduled.first().id)
        assertEquals(ReminderPlanner.WINDOW_DAYS, scheduled.size)
    }

    private fun TestScope.start(
        scheduler: FakeScheduler,
        session: FakeUserSession = session(LocalDataState.SignedOut),
        settings: ReminderSettingsRepository = FakeReminderSettings(),
        transactions: FakeTransactionRepository = FakeTransactionRepository(),
        clock: TestClock = morning(),
    ) = DefaultReminderCoordinator(
        session = session,
        settings = settings,
        transactions = transactions,
        scheduler = scheduler,
        planner = ReminderPlanner(),
        clock = clock,
        scope = backgroundScope,
        logger = Logger.withTag("test"),
    ).also { it.start() }

    private fun morning(): TestClock = TestClock(at(10, 0))

    private fun at(hour: Int, minute: Int): Instant = LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone)

    private fun session(state: LocalDataState) = FakeUserSession(state)

    private fun transaction(date: LocalDate) = Transaction(
        id = "t1",
        type = TransactionType.EXPENSE,
        status = TransactionStatus.CONFIRMED,
        accountId = "cash",
        counterAccountId = null,
        amount = Money(1_000, "VND"),
        categoryId = null,
        occurredOn = date,
        occurredAt = null,
        payee = null,
        note = null,
    )
}

private class FakeScheduler : LocalNotificationScheduler {
    val groups = mutableListOf<Pair<String, List<LocalNotification>>>()
    var cleared = 0

    override suspend fun replaceGroup(group: String, notifications: List<LocalNotification>) {
        groups += group to notifications
    }

    override suspend fun clearDelivered(group: String) {
        cleared += 1
    }
}

private class FakeReminderSettings : ReminderSettingsRepository {
    private val state = MutableStateFlow(ReminderSettings.DEFAULT)
    override fun observe(): Flow<ReminderSettings> = state
    override suspend fun setEnabled(enabled: Boolean) {
        state.value = state.value.copy(enabled = enabled)
    }
    override suspend fun setTime(time: LocalTime) {
        state.value = state.value.copy(time = time)
    }
}

private class FakeUserSession(initial: LocalDataState) : UserSession {
    override val state = MutableStateFlow(initial)
    override val notice: StateFlow<SessionNotice?> = MutableStateFlow(null)
    override fun start() = Unit
    override fun refresh() = Unit
    override fun acknowledgeNotice() = Unit
    override suspend fun wipeCurrentUser() = Unit
    override suspend fun discardDeletedAccount() = Unit
    override suspend fun otherUsersOnDevice(currentUserId: String): Set<String> = emptySet()
    override suspend fun usersOnDevice(): Set<String> = emptySet()
    override suspend fun wipe(userIds: Set<String>) = Unit
}
