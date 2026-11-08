package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.common.time.observeToday
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** "Hôm nay" theo lịch của máy, phát lại lúc 00:00 (ADR-007 G12): preset "Tháng này" của bộ lọc tự đổi khi qua tháng. */
class ObserveTodayUseCase(
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(): Flow<LocalDate> = observeToday(clock, timeZone)
}
