package com.uit.finance.feature.recurring

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.core.recurring.reminder.NotificationPermissionGate
import kotlinx.datetime.LocalDate

/** `NOON_2023_11_14` của ledger-testing: hôm nay là 14/11/2023. */
val TODAY = LocalDate(2023, 11, 14)

fun rent(
    id: String = "rent",
    day: Int = 5,
    amount: Long = 3_000_000,
    accountId: String = "cash",
    categoryId: String = "home",
    start: LocalDate = LocalDate(2023, 1, 1),
    remind: Int? = 2,
) = Recurring(
    id = id,
    name = "Tiền nhà",
    type = RecurringType.EXPENSE,
    currency = "VND",
    generationNotBefore = start,
    generationEndDate = null,
    remindDaysBefore = remind,
    revision = 1,
    revisions = listOf(RecurrenceRevision("$id-r1", start, start, accountId, categoryId, amount, null, RecurrenceRule.monthly(day), false)),
)

fun draft(id: String, on: LocalDate, amount: Long = 3_000_000, payee: String? = "Tiền nhà") = Transaction(
    id = id,
    type = TransactionType.EXPENSE,
    status = TransactionStatus.DRAFT,
    accountId = "cash",
    counterAccountId = null,
    amount = Money(amount, "VND"),
    categoryId = "home",
    occurredOn = on,
    occurredAt = null,
    payee = payee,
    note = null,
)

class FakePermissionGate(var granted: Boolean = false) : NotificationPermissionGate {
    var requests = 0
    override fun isGranted(): Boolean = granted
    override suspend fun request(): Boolean {
        requests++
        return granted
    }
}
