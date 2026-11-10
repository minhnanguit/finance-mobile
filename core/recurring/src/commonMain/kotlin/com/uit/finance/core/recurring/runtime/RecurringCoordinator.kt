@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.recurring.runtime

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.common.time.observeToday
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import com.uit.finance.core.recurring.domain.usecase.GenerateDueOccurrencesUseCase
import com.uit.finance.core.recurring.reminder.ReminderPlanner
import com.uit.finance.core.recurring.reminder.ReminderScheduler
import com.uit.finance.core.sync.engine.SyncEngine
import com.uit.finance.core.sync.engine.SyncStatus
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone

/**
 * Chạy generator và hẹn lại thông báo trong suốt đời process (ADR-008 RS12, mục 6 của plan):
 *
 * - khi mở sổ của user, khi qua ngày mới lúc app đang mở, và **sau mỗi vòng sync xong hết các trang pull**;
 * - mỗi khi danh sách khoản đổi thì hẹn lại thông báo;
 * - đăng xuất (không còn DB) thì huỷ mọi thông báo đang chờ và đang hiện.
 *
 * Một mutex cho mọi lần chạy; chạy chồng không sinh trùng vì repository kiểm "đã có" trong transaction.
 */
class RecurringCoordinator(
    private val databases: UserDatabaseProvider,
    private val syncEngine: SyncEngine,
    private val repository: RecurringRepository,
    private val generate: GenerateDueOccurrencesUseCase,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
    dispatchers: DispatcherProvider,
    private val logger: Logger,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val mutex = Mutex()
    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        if (job != null) return
        job = scope.launch {
            launch {
                databases.database.flatMapLatest { database ->
                    if (database == null) {
                        flowOf(false)
                    } else {
                        merge(
                            observeToday(clock).map { true },
                            syncEngine.status.filterIsInstance<SyncStatus.Completed>().map { true },
                        ).onStart { emit(true) }
                    }
                }.collect { hasUser ->
                    if (hasUser) runOnce() else reminders.cancelAll()
                }
            }
            launch {
                databases.database.flatMapLatest { database ->
                    if (database == null) flowOf(null) else combine(repository.observeRecurrings(), repository.observeLocalOccurrences()) { a, b -> a to b }
                }.debounce(RESCHEDULE_DEBOUNCE_MS).filter { it != null }.collect { pair ->
                    val (recurrings, local) = requireNotNull(pair)
                    reschedule(recurrings, local.map { it.transactionId }.toSet())
                }
            }
        }
    }

    /** Một lần sinh kỳ + hẹn lại thông báo. Gọi từ worker nền; chưa có user thì không làm gì. */
    suspend fun runOnce() {
        if (databases.database.value == null) return
        mutex.withLock {
            try {
                val report = generate()
                if (report.written > 0 || report.blocked > 0) {
                    logger.i { "Khoản định kỳ: sinh ${report.written} kỳ, ${report.blocked} khoản bị chặn" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w { "Sinh kỳ định kỳ lỗi: ${e::class.simpleName}" }
            }
        }
    }

    private fun reschedule(recurrings: List<Recurring>, recorded: Set<String>) {
        val zone = TimeZone.currentSystemDefault()
        val planned = ReminderPlanner.plan(recurrings, clock.now(), zone, clock.today(zone), recorded)
        try {
            reminders.replaceAll(planned)
        } catch (e: Exception) {
            logger.w { "Không hẹn được thông báo: ${e::class.simpleName}" }
        }
    }

    private companion object {
        const val RESCHEDULE_DEBOUNCE_MS = 500L
    }
}
