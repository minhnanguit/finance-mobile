package com.uit.finance.core.recurring.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.occurrence.LedgerOccurrenceWriter
import com.uit.finance.core.ledger.data.occurrence.OccurrenceDraft
import com.uit.finance.core.ledger.data.occurrence.OccurrenceWrite
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.network.api.model.RecurringPayloadCodec
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.recurring.data.local.RecurringStore
import com.uit.finance.core.recurring.data.local.rejectWith
import com.uit.finance.core.recurring.data.local.save
import com.uit.finance.core.recurring.data.local.syncRefs
import com.uit.finance.core.recurring.data.local.toDomain
import com.uit.finance.core.recurring.data.local.toPayload
import com.uit.finance.core.recurring.domain.model.LocalOccurrence
import com.uit.finance.core.recurring.domain.model.OccurrenceResult
import com.uit.finance.core.recurring.domain.model.OccurrenceStatus
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringDraft
import com.uit.finance.core.recurring.domain.model.RecurringErrorCode
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.core.recurring.domain.model.RevisionChange
import com.uit.finance.core.recurring.domain.model.ScheduledOccurrence
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import com.uit.finance.core.recurring.domain.validation.RecurringLimits
import com.uit.finance.core.recurring.domain.validation.RecurringRules
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.outbox.SyncRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Module **duy nhất** đụng bảng recurring (Konsist `RecurringDataRulesTest`). Bản ghi + timeline + op outbox +
 * `outbox_ref` luôn đi chung một transaction. Mỗi lần ghi gửi revision local làm điều kiện rồi tăng revision local
 * thêm 1 (RS9), op không gộp nên mỗi lần sửa là một bước CAS riêng trên server.
 */
internal class RecurringRepositoryImpl(
    private val store: RecurringStore,
    private val outbox: OutboxWriter,
    private val codec: RecurringPayloadCodec,
    private val occurrences: LedgerOccurrenceWriter,
    private val uuidGenerator: UuidGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : RecurringRepository {

    override fun observeRecurrings(): Flow<List<Recurring>> = store.observe(emptyList()) { database ->
        combine(
            database.recurringQueries.selectAll().asFlow().mapToList(dispatchers.io),
            database.recurringQueries.selectAllRevisions().asFlow().mapToList(dispatchers.io),
            database.outboxQueries.countAll().asFlow().mapToOne(dispatchers.io),
        ) { rows, revisions, _ ->
            val byOwner = revisions.groupBy { it.recurring_id }
            // Hai query phát lại độc lập: ngay sau khi ghi, `rows` có thể đã có khoản mới trong khi `revisions` còn là bản cũ.
            // Bỏ khoản chưa có timeline thay vì phát ra Recurring rỗng (currentRevision() sẽ crash); lần phát sau sẽ đủ.
            rows.mapNotNull { row ->
                byOwner[row.id]?.let { timeline -> row.toDomain(timeline, database.hasPending(row.id)) }
            }
        }
    }

    override fun observeLocalOccurrences(): Flow<List<LocalOccurrence>> = store.observe(emptyList()) { database ->
        database.recurringQueries.selectLocalOccurrences().asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun snapshot(): List<Recurring> = store.read(emptyList()) { database ->
        database.recurringQueries.selectAll().executeAsList().map { row -> database.load(row.id)!! }
    }

    override suspend fun create(draft: RecurringDraft): AppResult<Recurring> {
        val today = clock.today()
        val errors = RecurringRules.name(draft.name) + RecurringRules.amount(draft.amountMinor) +
            RecurringRules.note(draft.note) + RecurringRules.remind(draft.remindDaysBefore) +
            RecurringRules.rule(draft.rule) + RecurringRules.startDate(draft.startDate, today)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            val active = database.recurringQueries.countActive(today.minus(DatePeriod(days = 1)).toString()).executeAsOne()
            if (active >= RecurringLimits.MAX_ACTIVE) rejectWith("recurring", RecurringErrorCode.LIMIT_REACHED)
            val notBefore = if (draft.backfill || draft.startDate >= today) draft.startDate else today
            val recurring = Recurring(
                id = uuidGenerator.generate(),
                name = draft.name.trim(),
                type = draft.type,
                currency = draft.currency,
                generationNotBefore = notBefore,
                generationEndDate = draft.endDate,
                remindDaysBefore = draft.remindDaysBefore,
                revision = 0,
                revisions = listOf(
                    RecurrenceRevision(
                        id = uuidGenerator.generate(),
                        effectiveFrom = draft.startDate,
                        anchorDate = draft.startDate,
                        accountId = draft.accountId,
                        categoryId = draft.categoryId,
                        amountMinor = draft.amountMinor,
                        note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                        rule = draft.rule,
                        autoConfirm = draft.autoConfirm,
                    ),
                ),
            )
            persist(database, recurring)
        }
    }

    override suspend fun updateDetails(recurringId: String, name: String, remindDaysBefore: Int?): AppResult<Recurring> {
        val errors = RecurringRules.name(name) + RecurringRules.remind(remindDaysBefore)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))
        return mutate(recurringId) { it.copy(name = name.trim(), remindDaysBefore = remindDaysBefore) }
    }

    override suspend fun revise(recurringId: String, change: RevisionChange): AppResult<Recurring> {
        val today = clock.today()
        val errors = RecurringRules.amount(change.amountMinor) + RecurringRules.note(change.note) +
            RecurringRules.rule(change.rule) + RecurringRules.effectiveFrom(change.effectiveFrom, today)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return mutate(recurringId) { current ->
            val kept = current.revisions.filter { it.effectiveFrom < change.effectiveFrom }
            val base = kept.lastOrNull() ?: current.revisions.first()
            val anchor = if (base.rule.sameCadenceAs(change.rule)) base.anchorDate else change.effectiveFrom
            val next = RecurrenceRevision(
                id = uuidGenerator.generate(),
                effectiveFrom = change.effectiveFrom,
                anchorDate = minOf(anchor, change.effectiveFrom),
                accountId = change.accountId,
                categoryId = change.categoryId,
                amountMinor = change.amountMinor,
                note = change.note?.trim()?.takeIf { it.isNotEmpty() },
                rule = change.rule,
                autoConfirm = change.autoConfirm,
            )
            val timeline = kept + next
            if (timeline.size > RecurringLimits.MAX_REVISIONS) rejectWith("revisions", RecurringErrorCode.INVALID_FIELD)
            current.copy(revisions = timeline)
        }
    }

    override suspend fun end(recurringId: String, lastDate: LocalDate): AppResult<Recurring> {
        val yesterday = clock.today().minus(DatePeriod(days = 1))
        if (lastDate < yesterday) {
            return AppResult.Failure(AppError.Validation(listOf(FieldError("endDate", RecurringErrorCode.INVALID_FIELD))))
        }
        return mutate(recurringId) { current ->
            val existing = current.generationEndDate
            if (existing != null && lastDate > existing) rejectWith("endDate", RecurringErrorCode.IMMUTABLE_FIELD)
            current.copy(generationEndDate = lastDate)
        }
    }

    override suspend fun skipBlocked(recurringId: String): AppResult<Recurring> {
        val today = clock.today()
        return mutate(recurringId) { current ->
            current.copy(generationNotBefore = maxOf(current.generationNotBefore, today), blockedFrom = null)
        }
    }

    override suspend fun backfillBlocked(recurringId: String): AppResult<Recurring> = store.write { database ->
        val current = database.load(recurringId) ?: rejectWith("recurring", RecurringErrorCode.NOT_FOUND)
        database.recurringQueries.setBlockedFrom(null, recurringId)
        current.copy(blockedFrom = null)
    }

    override suspend fun delete(recurringId: String): AppResult<Unit> = store.write { database ->
        database.load(recurringId) ?: rejectWith("recurring", RecurringErrorCode.NOT_FOUND)
        database.recurringQueries.markDeleted(recurringId)
        outbox.delete(database, SyncEntity.RECURRING, recurringId)
    }

    override suspend fun recordOccurrence(
        recurring: Recurring,
        occurrence: ScheduledOccurrence,
        status: OccurrenceStatus,
    ): OccurrenceResult {
        val revision = occurrence.revision
        val draft = OccurrenceDraft(
            id = occurrence.transactionId,
            type = if (recurring.type == RecurringType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
            status = if (status == OccurrenceStatus.CONFIRMED) TransactionStatus.CONFIRMED else TransactionStatus.DRAFT,
            accountId = revision.accountId,
            categoryId = revision.categoryId,
            amountMinor = revision.amountMinor,
            currency = recurring.currency,
            occurredOn = occurrence.date,
            payee = recurring.name,
            note = revision.note,
            refs = listOf(SyncRef(SyncEntity.RECURRING, recurring.id)),
        )
        val result = store.write { database ->
            val currentRevision = database.recurringQueries.revisionOf(recurring.id).executeAsOneOrNull()
            if (currentRevision != recurring.revision) return@write OccurrenceResult.STALE
            when (occurrences.recordIfAbsent(database, draft)) {
                OccurrenceWrite.WRITTEN -> {
                    database.recurringQueries.insertLocalOccurrence(
                        occurrence.transactionId,
                        recurring.id,
                        occurrence.date.toString(),
                        revision.id,
                    )
                    OccurrenceResult.WRITTEN
                }
                OccurrenceWrite.ALREADY_EXISTS -> OccurrenceResult.ALREADY_EXISTS
                OccurrenceWrite.BLOCKED -> {
                    if (recurring.blockedFrom == null) {
                        database.recurringQueries.setBlockedFrom(occurrence.date.toString(), recurring.id)
                    }
                    OccurrenceResult.BLOCKED
                }
            }
        }
        return (result as? AppResult.Success)?.value ?: OccurrenceResult.STALE
    }

    /** Đọc bản local chưa xoá, áp [change], ghi lại với revision local làm điều kiện. */
    private suspend fun mutate(recurringId: String, change: (Recurring) -> Recurring): AppResult<Recurring> =
        store.write { database ->
            val current = database.load(recurringId) ?: rejectWith("recurring", RecurringErrorCode.NOT_FOUND)
            val next = change(current)
            if (next == current) current else persist(database, next)
        }

    /**
     * Ghi bản ghi với revision local + 1 và một op gửi revision local hiện tại làm điều kiện (RS8, RS9). Op không gộp
     * vào op trước: op trước có thể đang bay, gộp sẽ mất bước CAS của nó.
     */
    private fun persist(database: FinanceDatabase, recurring: Recurring): Recurring {
        val expected = recurring.revision
        val saved = recurring.copy(revision = expected + 1)
        database.save(saved)
        outbox.upsert(
            database,
            SyncEntity.RECURRING,
            saved.id,
            codec.encode(saved.toPayload(expectedRevision = expected)),
            saved.syncRefs(),
            mergeIntoTail = false,
        )
        return saved.copy(hasPendingSync = true)
    }

    private fun FinanceDatabase.load(recurringId: String): Recurring? =
        recurringQueries.selectById(recurringId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain(recurringQueries.selectRevisions(recurringId).executeAsList(), hasPending(recurringId))

    private fun FinanceDatabase.hasPending(recurringId: String): Boolean =
        outboxQueries.hasPendingFor(SyncEntity.RECURRING, recurringId).executeAsOne() > 0
}
