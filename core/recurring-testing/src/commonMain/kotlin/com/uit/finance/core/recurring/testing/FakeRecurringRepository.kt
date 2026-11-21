package com.uit.finance.core.recurring.testing

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.recurring.domain.model.LocalOccurrence
import com.uit.finance.core.recurring.domain.model.OccurrenceResult
import com.uit.finance.core.recurring.domain.model.OccurrenceStatus
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringDraft
import com.uit.finance.core.recurring.domain.model.RevisionChange
import com.uit.finance.core.recurring.domain.model.ScheduledOccurrence
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * Fake trong bộ nhớ cho [RecurringRepository]. Không kiểm luật (đó là việc của `RecurringRepositoryTest` trên SQLite
 * thật); chỉ lưu, phát Flow và ghi lại lời gọi để test ViewModel / use case. [failNext] làm lần ghi kế tiếp trả lỗi.
 */
class FakeRecurringRepository : RecurringRepository {
    val recurrings = MutableStateFlow<List<Recurring>>(emptyList())
    val localOccurrences = MutableStateFlow<List<LocalOccurrence>>(emptyList())
    var failNext: AppError? = null

    val created = mutableListOf<RecurringDraft>()
    val revised = mutableListOf<Pair<String, RevisionChange>>()
    val ended = mutableListOf<Pair<String, LocalDate>>()
    val deleted = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    val backfilled = mutableListOf<String>()
    val recorded = mutableListOf<Pair<ScheduledOccurrence, OccurrenceStatus>>()
    private var nextId = 0

    override fun observeRecurrings(): Flow<List<Recurring>> = recurrings

    override fun observeLocalOccurrences(): Flow<List<LocalOccurrence>> = localOccurrences

    override suspend fun snapshot(): List<Recurring> = recurrings.value

    override suspend fun create(draft: RecurringDraft): AppResult<Recurring> = write {
        created += draft
        val id = "recurring-${++nextId}"
        Recurring(
            id = id,
            name = draft.name,
            type = draft.type,
            currency = draft.currency,
            generationNotBefore = draft.startDate,
            generationEndDate = draft.endDate,
            remindDaysBefore = draft.remindDaysBefore,
            revision = 1,
            revisions = listOf(
                RecurrenceRevision(
                    "$id-r1", draft.startDate, draft.startDate, draft.accountId, draft.categoryId,
                    draft.amountMinor, draft.note, draft.rule, draft.autoConfirm,
                ),
            ),
            hasPendingSync = true,
        ).also { recurring -> recurrings.update { it + recurring } }
    }

    override suspend fun updateDetails(recurringId: String, name: String, remindDaysBefore: Int?): AppResult<Recurring> =
        replace(recurringId) { it.copy(name = name, remindDaysBefore = remindDaysBefore) }

    override suspend fun revise(recurringId: String, change: RevisionChange): AppResult<Recurring> {
        revised += recurringId to change
        return replace(recurringId) { current ->
            val kept = current.revisions.filter { it.effectiveFrom < change.effectiveFrom }
            current.copy(
                revisions = kept + RecurrenceRevision(
                    "$recurringId-r${current.revisions.size + 1}", change.effectiveFrom, change.effectiveFrom,
                    change.accountId, change.categoryId, change.amountMinor, change.note, change.rule, change.autoConfirm,
                ),
            )
        }
    }

    override suspend fun end(recurringId: String, lastDate: LocalDate): AppResult<Recurring> {
        ended += recurringId to lastDate
        return replace(recurringId) { it.copy(generationEndDate = lastDate) }
    }

    override suspend fun skipBlocked(recurringId: String): AppResult<Recurring> {
        skipped += recurringId
        return replace(recurringId) { it.copy(blockedFrom = null) }
    }

    override suspend fun backfillBlocked(recurringId: String): AppResult<Recurring> {
        backfilled += recurringId
        return replace(recurringId) { it.copy(blockedFrom = null) }
    }

    override suspend fun delete(recurringId: String): AppResult<Unit> = write {
        deleted += recurringId
        recurrings.update { list -> list.filterNot { it.id == recurringId } }
    }

    override suspend fun recordOccurrence(
        recurring: Recurring,
        occurrence: ScheduledOccurrence,
        status: OccurrenceStatus,
    ): OccurrenceResult {
        if (recorded.any { it.first.transactionId == occurrence.transactionId }) return OccurrenceResult.ALREADY_EXISTS
        recorded += occurrence to status
        return OccurrenceResult.WRITTEN
    }

    private fun replace(recurringId: String, change: (Recurring) -> Recurring): AppResult<Recurring> = write {
        val updated = change(recurrings.value.first { it.id == recurringId })
        recurrings.update { list -> list.map { if (it.id == recurringId) updated else it } }
        updated
    }

    private inline fun <T> write(block: () -> T): AppResult<T> {
        failNext?.let {
            failNext = null
            return AppResult.Failure(it)
        }
        return AppResult.Success(block())
    }
}
