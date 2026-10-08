@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.ledger.data.local

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.CategoryPayload
import com.uit.finance.core.network.api.model.TransactionPayload
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import com.uit.finance.core.database.Account as AccountRow
import com.uit.finance.core.database.Category as CategoryRow
import com.uit.finance.core.database.Ledger_transaction as TransactionRow

// ---- Dòng DB → domain ----

internal fun AccountRow.toDomain() = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    openingBalance = Money(opening_balance_minor, currency),
    sortOrder = sort_order.toInt(),
    archived = archived != 0L,
)

internal fun CategoryRow.toDomain() = Category(
    id = id,
    kind = CategoryKind.valueOf(kind),
    name = name,
    parentId = parent_id,
    icon = icon,
    color = color,
    templateKey = template_key,
    archived = archived != 0L,
)

internal fun TransactionRow.toDomain() = Transaction(
    id = id,
    type = TransactionType.valueOf(type),
    status = TransactionStatus.valueOf(status),
    accountId = account_id,
    counterAccountId = counter_account_id,
    amount = Money(amount_minor, currency),
    categoryId = category_id,
    occurredOn = LocalDate.parse(occurred_on),
    occurredAt = occurred_at?.let(Instant::fromEpochMilliseconds),
    payee = payee,
    note = note,
)

// ---- Domain → dòng DB (luôn là bản chưa xoá) ----

internal fun FinanceDatabase.save(account: Account) = accountQueries.upsert(
    account.id,
    account.name,
    account.type.name,
    account.currency,
    account.openingBalance.amountMinor,
    account.sortOrder.toLong(),
    account.archived.toLong(),
    0L,
)

internal fun FinanceDatabase.save(category: Category) = categoryQueries.upsert(
    category.id,
    category.kind.name,
    category.name,
    category.parentId,
    category.icon,
    category.color,
    category.templateKey,
    category.archived.toLong(),
    0L,
)

internal fun FinanceDatabase.save(transaction: Transaction) = ledgerTransactionQueries.upsert(
    transaction.id,
    transaction.type.name,
    transaction.status.name,
    transaction.accountId,
    transaction.counterAccountId,
    transaction.amount.amountMinor,
    transaction.amount.currency,
    transaction.categoryId,
    transaction.occurredOn.toString(),
    transaction.occurredAt?.toEpochMilliseconds(),
    transaction.payee,
    transaction.note,
    0L,
)

// ---- Domain ↔ payload của contract ----

internal fun Account.toPayload() = AccountPayload(
    name = name,
    type = type.name,
    currency = currency,
    openingBalanceMinor = openingBalance.amountMinor,
    sortOrder = sortOrder,
    archived = archived,
)

internal fun AccountPayload.toDomain(id: String) = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    openingBalance = Money(openingBalanceMinor, currency),
    sortOrder = sortOrder,
    archived = archived,
)

internal fun Category.toPayload() = CategoryPayload(
    kind = kind.name,
    name = name,
    parentId = parentId,
    icon = icon,
    color = color,
    archived = archived,
)

internal fun CategoryPayload.toDomain(id: String) = Category(
    id = id,
    kind = CategoryKind.valueOf(kind),
    name = name,
    parentId = parentId,
    icon = icon,
    color = color,
    templateKey = templateKey,
    archived = archived,
)

internal fun Transaction.toPayload() = TransactionPayload(
    type = type.name,
    status = status.name,
    accountId = accountId,
    counterAccountId = counterAccountId,
    amountMinor = amount.amountMinor,
    currency = amount.currency,
    categoryId = categoryId,
    occurredOn = occurredOn,
    occurredAt = occurredAt,
    payee = payee,
    note = note,
)

internal fun TransactionPayload.toDomain(id: String) = Transaction(
    id = id,
    type = TransactionType.valueOf(type),
    status = TransactionStatus.valueOf(status),
    accountId = accountId,
    counterAccountId = counterAccountId,
    amount = Money(amountMinor, currency),
    categoryId = categoryId,
    occurredOn = occurredOn,
    occurredAt = occurredAt,
    payee = payee,
    note = note,
)
