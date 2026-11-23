package com.uit.finance.core.recurring.data.local

import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.network.api.model.RecurrenceRevisionPayload
import com.uit.finance.core.network.api.model.RecurringPayload
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.LocalOccurrence
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.core.sync.outbox.SyncRef
import kotlinx.datetime.LocalDate
import com.uit.finance.core.database.Recurring as RecurringRow
import com.uit.finance.core.database.Recurring_occurrence_local as LocalOccurrenceRow
import com.uit.finance.core.database.Recurring_revision as RevisionRow

// ---- Dòng DB → domain ----

internal fun RecurringRow.toDomain(revisions: List<RevisionRow>, hasPendingSync: Boolean) = Recurring(
    id = id,
    name = name,
    type = RecurringType.valueOf(type),
    currency = currency,
    generationNotBefore = LocalDate.parse(generation_not_before),
    generationEndDate = generation_end_date?.let(LocalDate::parse),
    remindDaysBefore = remind_days_before?.toInt(),
    revision = revision,
    revisions = revisions.map { it.toDomain() },
    blockedFrom = blocked_from?.let(LocalDate::parse),
    hasPendingSync = hasPendingSync,
)

internal fun RevisionRow.toDomain() = RecurrenceRevision(
    id = id,
    effectiveFrom = LocalDate.parse(effective_from),
    anchorDate = LocalDate.parse(anchor_date),
    accountId = account_id,
    categoryId = category_id,
    amountMinor = amount_minor,
    note = note,
    rule = RecurrenceRule(
        frequency = Frequency.valueOf(frequency),
        interval = interval_count.toInt(),
        weekday = weekday?.toInt(),
        month = month?.toInt(),
        dayOfMonth = day_of_month?.toInt(),
    ),
    autoConfirm = auto_confirm != 0L,
)

internal fun LocalOccurrenceRow.toDomain() = LocalOccurrence(
    transactionId = transaction_id,
    recurringId = recurring_id,
    scheduledDate = LocalDate.parse(scheduled_date),
    revisionId = revision_id,
)

// ---- Domain → dòng DB (bản chưa xoá; giữ blocked_from local) ----

internal fun FinanceDatabase.save(recurring: Recurring) {
    recurringQueries.upsert(
        recurring.id,
        recurring.name,
        recurring.type.name,
        recurring.currency,
        recurring.generationNotBefore.toString(),
        recurring.generationEndDate?.toString(),
        recurring.remindDaysBefore?.toLong(),
        recurring.revision,
        recurring.blockedFrom?.toString(),
        0L,
    )
    recurringQueries.deleteRevisions(recurring.id)
    recurring.revisions.forEach { r ->
        recurringQueries.insertRevision(
            r.id,
            recurring.id,
            r.effectiveFrom.toString(),
            r.anchorDate.toString(),
            r.accountId,
            r.categoryId,
            r.amountMinor,
            r.note,
            r.rule.frequency.name,
            r.rule.interval.toLong(),
            r.rule.weekday?.toLong(),
            r.rule.month?.toLong(),
            r.rule.dayOfMonth?.toLong(),
            if (r.autoConfirm) 1L else 0L,
        )
    }
}

// ---- Domain ↔ payload của contract ----

/** [expectedRevision] là revision máy dựa vào khi gửi op (RS8). */
internal fun Recurring.toPayload(expectedRevision: Long) = RecurringPayload(
    name = name,
    type = type.name,
    currency = currency,
    generationNotBefore = generationNotBefore,
    generationEndDate = generationEndDate,
    remindDaysBefore = remindDaysBefore,
    revision = expectedRevision,
    revisions = revisions.map { r ->
        RecurrenceRevisionPayload(
            id = r.id,
            effectiveFrom = r.effectiveFrom,
            anchorDate = r.anchorDate,
            accountId = r.accountId,
            categoryId = r.categoryId,
            amountMinor = r.amountMinor,
            note = r.note,
            frequency = r.rule.frequency.name,
            interval = r.rule.interval,
            weekday = r.rule.weekday,
            month = r.rule.month,
            dayOfMonth = r.rule.dayOfMonth,
            autoConfirm = r.autoConfirm,
        )
    },
)

/** Bản server: [RecurringPayload.revision] là revision hiện tại. */
internal fun RecurringPayload.toDomain(id: String, blockedFrom: LocalDate?) = Recurring(
    id = id,
    name = name,
    type = RecurringType.valueOf(type),
    currency = currency,
    generationNotBefore = generationNotBefore,
    generationEndDate = generationEndDate,
    remindDaysBefore = remindDaysBefore,
    revision = revision,
    revisions = revisions.map { r ->
        RecurrenceRevision(
            id = r.id,
            effectiveFrom = r.effectiveFrom,
            anchorDate = r.anchorDate,
            accountId = r.accountId,
            categoryId = r.categoryId,
            amountMinor = r.amountMinor,
            note = r.note,
            rule = RecurrenceRule(Frequency.valueOf(r.frequency), r.interval, r.weekday, r.month, r.dayOfMonth),
            autoConfirm = r.autoConfirm,
        )
    },
    blockedFrom = blockedFrom,
)

/** Op khoản định kỳ trỏ tới ví và danh mục của mọi phiên bản (RS10). */
internal fun Recurring.syncRefs(): List<SyncRef> = revisions.flatMap {
    listOf(SyncRef(SyncEntity.ACCOUNT, it.accountId), SyncRef(SyncEntity.CATEGORY, it.categoryId))
}.distinct()
