package com.uit.finance.feature.recurring.domain.usecase

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.common.time.observeToday
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.recurring.domain.engine.RecurrenceEngine
import com.uit.finance.core.recurring.domain.model.LocalOccurrence
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import com.uit.finance.feature.recurring.domain.model.AttentionItem
import com.uit.finance.feature.recurring.domain.model.AttentionReason
import com.uit.finance.feature.recurring.domain.model.OccurrenceItem
import com.uit.finance.feature.recurring.domain.model.RecurringOverview
import com.uit.finance.feature.recurring.domain.model.RecurringSummary
import com.uit.finance.feature.recurring.domain.model.UpcomingItem
import com.uit.finance.feature.recurring.domain.model.UpcomingTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Gom khoản định kỳ + giao dịch nháp + ví/danh mục + "hôm nay" thành toàn cảnh màn (plan mục 2.1, ADR-008 R13, R16).
 *
 * - "Cần xác nhận": nháp có id trùng một kỳ theo lịch hiện hành (tính lại id UUIDv5, không cần cột nguồn).
 * - "Cần kiểm tra": nháp do máy này sinh mà timeline hiện hành không còn kỳ đó (R16). Không tự xoá.
 * - "Sắp đến hạn": từ ngày mai tới hôm nay + 29, không gồm khoản đang bị chặn; kỳ hôm nay do generator lo.
 */
class ObserveRecurringOverviewUseCase(
    private val recurringRepository: RecurringRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<RecurringOverview> {
        val references = combine(accountRepository.observeAccounts(), categoryRepository.observeCategories()) { a, c -> a to c }
        return combine(
            recurringRepository.observeRecurrings(),
            recurringRepository.observeLocalOccurrences(),
            transactionRepository.observeFiltered(TransactionFilter(status = TransactionStatus.DRAFT), DRAFT_LIMIT),
            references,
            observeToday(clock),
        ) { recurrings, local, drafts, (accounts, categories), today ->
            build(recurrings, local, drafts, accounts, categories, today)
        }
    }

    private fun build(
        recurrings: List<Recurring>,
        local: List<LocalOccurrence>,
        drafts: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
        today: LocalDate,
    ): RecurringOverview {
        val byId = recurrings.associateBy { it.id }
        val canonical = recurrings.flatMap { recurring ->
            RecurrenceEngine.occurrences(recurring, today.minus(DatePeriod(days = LOOKBACK_DAYS)), today)
                .map { it.transactionId to recurring }
        }.toMap()
        val localById = local.associateBy { it.transactionId }

        val toConfirm = mutableListOf<OccurrenceItem>()
        val toReview = mutableListOf<OccurrenceItem>()
        for (draft in drafts) {
            val owner = canonical[draft.id]
            val mine = localById[draft.id]
            val type = if (draft.type.name == RecurringType.INCOME.name) RecurringType.INCOME else RecurringType.EXPENSE
            when {
                owner != null -> toConfirm += OccurrenceItem(draft.id, owner.id, draft.payee ?: owner.name, draft.occurredOn, draft.amount, type)
                mine != null -> toReview += OccurrenceItem(
                    draft.id, mine.recurringId, draft.payee ?: byId[mine.recurringId]?.name.orEmpty(), draft.occurredOn, draft.amount, type,
                )
            }
        }

        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val attention = recurrings.filterNot { it.isEnded(today) }.mapNotNull { recurring ->
            val revision = recurring.currentRevision(today)
            val account = accountsById[revision.accountId]
            val category = categoriesById[revision.categoryId]
            when {
                recurring.blockedFrom != null -> AttentionItem(recurring, AttentionReason.BLOCKED)
                account == null || account.archived -> AttentionItem(recurring, AttentionReason.ACCOUNT_UNAVAILABLE)
                category == null || category.archived -> AttentionItem(recurring, AttentionReason.CATEGORY_UNAVAILABLE)
                else -> null
            }
        }
        val blockedIds = attention.map { it.recurring.id }.toSet()

        val horizon = today.plus(DatePeriod(days = UPCOMING_DAYS - 1))
        val upcoming = recurrings.filter { it.id !in blockedIds }.flatMap { recurring ->
            RecurrenceEngine.occurrences(recurring, today.plus(DatePeriod(days = 1)), horizon).map {
                UpcomingItem(recurring.id, recurring.name, it.date, Money(it.revision.amountMinor, recurring.currency), recurring.type)
            }
        }.sortedWith(compareBy({ it.date }, { it.name }))

        val totals = upcoming.groupBy { it.amount.currency }.map { (currency, items) ->
            UpcomingTotal(
                currency = currency,
                expense = Money(items.filter { it.type == RecurringType.EXPENSE }.sumOf { it.amount.amountMinor }, currency),
                income = Money(items.filter { it.type == RecurringType.INCOME }.sumOf { it.amount.amountMinor }, currency),
            )
        }.sortedBy { it.currency }

        val summaries = recurrings.map { recurring ->
            RecurringSummary(
                recurring = recurring,
                next = RecurrenceEngine.next(recurring, today.minus(DatePeriod(days = 1)))?.date,
                isEnded = recurring.isEnded(today),
                needsAttention = recurring.id in blockedIds,
            )
        }.sortedWith(compareBy({ it.isEnded }, { it.next ?: LocalDate(9999, 12, 31) }))

        return RecurringOverview(
            today = today,
            attention = attention,
            toConfirm = toConfirm.sortedByDescending { it.date },
            toReview = toReview.sortedByDescending { it.date },
            upcoming = upcoming,
            upcomingTotals = totals,
            recurrings = summaries,
        )
    }

    private companion object {
        const val DRAFT_LIMIT = 500
        const val LOOKBACK_DAYS = 400
        const val UPCOMING_DAYS = 30
    }
}
