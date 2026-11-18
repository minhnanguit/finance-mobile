package com.uit.finance.core.recurring.domain.usecase

import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.recurring.domain.engine.RecurrenceEngine
import com.uit.finance.core.recurring.domain.model.OccurrenceResult
import com.uit.finance.core.recurring.domain.model.OccurrenceStatus
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import com.uit.finance.core.recurring.domain.validation.RecurringLimits
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/** Số kỳ đã ghi / bị chặn trong một lần chạy. */
data class GenerationReport(val written: Int, val blocked: Int, val stale: Int) {
    companion object {
        val EMPTY = GenerationReport(0, 0, 0)
    }
}

/**
 * Sinh giao dịch cho các kỳ đã đến hạn (ADR-009 R5–R9, R13, RS13). Không cần mạng; chạy lại bao nhiêu lần cũng không
 * trùng vì id kỳ cố định và repository kiểm "đã có" trong cùng transaction.
 *
 * - Chỉ xét kỳ trong `[max(hôm nay − 89, generationNotBefore), min(hôm nay, ngày kết thúc)]` (R7).
 * - Kỳ trước hôm nay luôn là nháp; kỳ hôm nay tự xác nhận khi phiên bản bật và lịch đã được server nhận (R9).
 * - Khoản đang bị chặn (R13) chờ user quyết, không sinh gì.
 * - Revision đổi giữa lúc tính: bỏ khoản đó, lần chạy sau tính lại theo lịch mới.
 */
class GenerateDueOccurrencesUseCase(
    private val repository: RecurringRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): GenerationReport {
        val today = clock.today()
        val earliest = today.minus(DatePeriod(days = RecurringLimits.CATCH_UP_DAYS - 1))
        var written = 0
        var blocked = 0
        var stale = 0
        for (recurring in repository.snapshot()) {
            if (recurring.blockedFrom != null) continue
            val from = maxOf(earliest, recurring.generationNotBefore)
            for (occurrence in RecurrenceEngine.occurrences(recurring, from, today)) {
                val status = if (occurrence.date == today && occurrence.revision.autoConfirm && !recurring.hasPendingSync) {
                    OccurrenceStatus.CONFIRMED
                } else {
                    OccurrenceStatus.DRAFT
                }
                when (repository.recordOccurrence(recurring, occurrence, status)) {
                    OccurrenceResult.WRITTEN -> written++
                    OccurrenceResult.ALREADY_EXISTS -> Unit
                    OccurrenceResult.BLOCKED -> {
                        blocked++
                        break
                    }
                    OccurrenceResult.STALE -> {
                        stale++
                        break
                    }
                }
            }
        }
        return GenerationReport(written, blocked, stale)
    }
}
