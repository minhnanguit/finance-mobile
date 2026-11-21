package com.uit.finance.feature.recurring.presentation.list

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.common.result.onFailure
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.core.sync.domain.usecase.AcknowledgeSyncIssuesUseCase
import com.uit.finance.core.sync.domain.usecase.ObserveSyncOverviewUseCase
import com.uit.finance.core.sync.domain.usecase.SyncNowUseCase
import com.uit.finance.core.ui.sync.toSyncMessage
import com.uit.finance.feature.recurring.domain.model.AttentionReason
import com.uit.finance.feature.recurring.domain.model.OccurrenceItem
import com.uit.finance.feature.recurring.domain.model.RecurringOverview
import com.uit.finance.feature.recurring.domain.usecase.ConfirmOccurrenceUseCase
import com.uit.finance.feature.recurring.domain.usecase.ObserveRecurringOverviewUseCase
import com.uit.finance.feature.recurring.domain.usecase.ResolveBlockedRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.SkipOccurrenceUseCase
import com.uit.finance.feature.recurring.presentation.dayMonthLabel
import com.uit.finance.feature.recurring.presentation.describe
import com.uit.finance.feature.recurring.presentation.toRecurringUiText
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Màn "Khoản định kỳ" (plan mục 2.1): cần chú ý, cần xác nhận, cần kiểm tra, sắp đến hạn, tất cả khoản. */
internal class RecurringListViewModel(
    observeOverview: ObserveRecurringOverviewUseCase,
    observeSync: ObserveSyncOverviewUseCase,
    private val confirmOccurrence: ConfirmOccurrenceUseCase,
    private val skipOccurrence: SkipOccurrenceUseCase,
    private val resolveBlocked: ResolveBlockedRecurringUseCase,
    private val syncNow: SyncNowUseCase,
    private val acknowledgeSyncIssues: AcknowledgeSyncIssuesUseCase,
    private val money: MoneyFormatter,
) : MviViewModel<RecurringListState, RecurringListIntent, RecurringListEffect>(RecurringListState()) {

    init {
        observeOverview()
            .onEach { overview -> setState { withOverview(overview) } }
            .launchIn(viewModelScope)
        observeSync()
            .onEach { overview -> setState { copy(sync = overview) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: RecurringListIntent) = when (intent) {
        RecurringListIntent.AddClicked -> sendEffect(RecurringListEffect.OpenEditor(null))
        is RecurringListIntent.RecurringClicked -> sendEffect(RecurringListEffect.OpenEditor(intent.recurringId))
        is RecurringListIntent.OccurrenceClicked -> sendEffect(RecurringListEffect.OpenTransaction(intent.transactionId))
        is RecurringListIntent.ConfirmClicked -> confirm(intent.transactionId)
        is RecurringListIntent.SkipClicked -> {
            val item = (currentState.toConfirm + currentState.toReview).firstOrNull { it.transactionId == intent.transactionId }
            setState { copy(skipConfirm = item) }
        }
        RecurringListIntent.SkipConfirmed -> skip()
        RecurringListIntent.SkipDismissed -> setState { copy(skipConfirm = null) }
        is RecurringListIntent.BackfillClicked -> resolve(intent.recurringId, backfill = true)
        is RecurringListIntent.SkipBlockedClicked -> resolve(intent.recurringId, backfill = false)
        RecurringListIntent.SyncNow -> startSync()
        RecurringListIntent.AcknowledgeSyncIssues -> {
            viewModelScope.launch { acknowledgeSyncIssues() }
            Unit
        }
        RecurringListIntent.MessageShown -> setState { copy(message = null) }
        RecurringListIntent.BackClicked -> sendEffect(RecurringListEffect.Close)
    }

    private fun confirm(transactionId: String) {
        if (transactionId in currentState.busyIds) return
        setState { copy(busyIds = busyIds + transactionId) }
        viewModelScope.launch {
            confirmOccurrence(transactionId).fold(
                onSuccess = { setState { copy(busyIds = busyIds - transactionId, message = UiText.Key("recurring.confirmed", "Đã ghi vào sổ")) } },
                onFailure = { error -> setState { copy(busyIds = busyIds - transactionId, message = error.toRecurringUiText()) } },
            )
        }
    }

    private fun skip() {
        val item = currentState.skipConfirm ?: return
        setState { copy(skipConfirm = null, busyIds = busyIds + item.transactionId) }
        viewModelScope.launch {
            skipOccurrence(item.transactionId).fold(
                onSuccess = { setState { copy(busyIds = busyIds - item.transactionId, message = UiText.Key("recurring.skipped", "Đã bỏ qua kỳ này")) } },
                onFailure = { error -> setState { copy(busyIds = busyIds - item.transactionId, message = error.toRecurringUiText()) } },
            )
        }
    }

    private fun resolve(recurringId: String, backfill: Boolean) {
        viewModelScope.launch {
            resolveBlocked(recurringId, backfill).onFailure { error -> setState { copy(message = error.toRecurringUiText()) } }
        }
    }

    private fun startSync() {
        if (currentState.isRefreshing) return
        setState { copy(isRefreshing = true) }
        viewModelScope.launch {
            syncNow().onFailure { error -> setState { copy(message = error.toSyncMessage()) } }
            setState { copy(isRefreshing = false) }
        }
    }

    private fun RecurringListState.withOverview(overview: RecurringOverview): RecurringListState = copy(
        isLoading = false,
        attention = overview.attention.map { item ->
            AttentionUi(
                recurringId = item.recurring.id,
                name = item.recurring.name,
                reason = when (item.reason) {
                    AttentionReason.BLOCKED ->
                        "Không ghi được từ ${item.recurring.blockedFrom?.dayMonthLabel().orEmpty()} vì ví hoặc danh mục đã lưu trữ. " +
                            "Khôi phục chúng rồi chọn ghi bù hoặc bỏ qua các kỳ đó."
                    AttentionReason.ACCOUNT_UNAVAILABLE -> "Ví của khoản này đã lưu trữ hoặc chưa đồng bộ xong: tạm không tự ghi."
                    AttentionReason.CATEGORY_UNAVAILABLE -> "Danh mục của khoản này đã lưu trữ hoặc chưa đồng bộ xong: tạm không tự ghi."
                },
                canResolve = item.reason == AttentionReason.BLOCKED,
            )
        },
        toConfirm = overview.toConfirm.map { it.toUi(overview.today) },
        toReview = overview.toReview.map { it.toUi(overview.today) },
        upcoming = overview.upcoming.groupBy { it.date }.map { (date, items) ->
            UpcomingDayUi(
                dateLabel = date.label(overview.today),
                items = items.map { UpcomingUi("${it.recurringId}/$date", it.recurringId, it.name, it.amount.toUi(it.type)) },
            )
        },
        upcomingTotals = overview.upcomingTotals.map { total ->
            listOfNotNull(
                total.expense.takeIf { it.amountMinor > 0 }?.let { "Chi ${money.format(it).text}" },
                total.income.takeIf { it.amountMinor > 0 }?.let { "Thu ${money.format(it).text}" },
            ).joinToString(" · ")
        },
        recurrings = overview.recurrings.map { summary ->
            val recurring = summary.recurring
            val revision = recurring.currentRevision(overview.today)
            RecurringItemUi(
                id = recurring.id,
                name = recurring.name,
                schedule = revision.rule.describe(),
                nextLabel = when {
                    summary.isEnded -> "Đã kết thúc"
                    summary.next != null -> "Lần tới ${summary.next.label(overview.today)}"
                    else -> "Không còn kỳ nào"
                },
                amount = Money(revision.amountMinor, recurring.currency).toUi(recurring.type),
                isEnded = summary.isEnded,
                needsAttention = summary.needsAttention,
            )
        },
    )

    private fun OccurrenceItem.toUi(today: LocalDate) = OccurrenceUi(
        transactionId = transactionId,
        name = name,
        dateLabel = date.label(today),
        amount = amount.toUi(type),
    )

    private fun Money.toUi(type: RecurringType): MoneyUi =
        money.format(this).let { MoneyUi(it.currency, it.amount, isIncome = type == RecurringType.INCOME) }

    private fun LocalDate.label(today: LocalDate): String = when (this) {
        today -> "Hôm nay"
        else -> "${weekdayShort()} ${dayMonthLabel()}"
    }

    private fun LocalDate.weekdayShort(): String = when (dayOfWeek.ordinal) {
        6 -> "CN"
        else -> "T${dayOfWeek.ordinal + 2}"
    }
}
