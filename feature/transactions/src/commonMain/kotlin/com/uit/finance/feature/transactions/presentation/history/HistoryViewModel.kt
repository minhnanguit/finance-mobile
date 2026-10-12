package com.uit.finance.feature.transactions.presentation.history

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.onFailure
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.toRelativeLabel
import com.uit.finance.core.sync.domain.usecase.AcknowledgeSyncIssuesUseCase
import com.uit.finance.core.sync.domain.usecase.ObserveSyncOverviewUseCase
import com.uit.finance.core.sync.domain.usecase.SyncNowUseCase
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.core.ui.sync.toSyncMessage
import com.uit.finance.core.ui.transaction.TransactionItemFormatter
import com.uit.finance.feature.transactions.domain.model.TransactionDetails
import com.uit.finance.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionHistoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Lịch sử giao dịch. Cuộn vô hạn = nới [limit] thêm [PAGE_SIZE] mỗi lần chạm cuối danh sách.
 *
 * Vuốt xoá có hoàn tác bằng **xoá trễ**: dòng bị ẩn ngay, chỉ gọi xoá thật khi hết thời gian hoàn tác, khi vuốt xoá
 * dòng khác, hoặc khi rời màn hình. Không xoá rồi ghi lại để hoàn tác, vì server cho xoá luôn thắng (S4): bản ghi
 * lại sẽ bị `CONFLICT` và biến mất ở lần sync sau.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
internal class HistoryViewModel(
    observeHistory: ObserveTransactionHistoryUseCase,
    observeSync: ObserveSyncOverviewUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val syncNow: SyncNowUseCase,
    private val acknowledgeSyncIssues: AcknowledgeSyncIssuesUseCase,
    private val formatter: TransactionItemFormatter,
    private val clock: Clock,
) : MviViewModel<HistoryState, HistoryIntent, HistoryEffect>(HistoryState()) {

    private val limit = MutableStateFlow(PAGE_SIZE)
    private var latest: List<TransactionDetails> = emptyList()

    /** Dòng đã gọi xoá nhưng Flow chưa phát bản mới: tiếp tục ẩn để không nháy lại. */
    private val hiddenUntilGone = mutableSetOf<String>()

    init {
        limit
            .flatMapLatest { observeHistory(it) }
            .onEach { details ->
                latest = details
                setState { rebuild(canLoadMore = details.size >= limit.value) }
            }
            .launchIn(viewModelScope)
        observeSync()
            .onEach { overview -> setState { copy(sync = overview) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: HistoryIntent) = when (intent) {
        HistoryIntent.LoadMore -> loadMore()
        HistoryIntent.AddClicked -> sendEffect(HistoryEffect.OpenEditor(null))
        is HistoryIntent.ItemClicked -> sendEffect(HistoryEffect.OpenEditor(intent.transactionId))
        HistoryIntent.CategoriesClicked -> sendEffect(HistoryEffect.OpenCategories)
        is HistoryIntent.DeleteSwiped -> deleteLater(intent.transactionId)
        HistoryIntent.UndoDelete -> setState { copy(pendingDeleteId = null).rebuild() }
        HistoryIntent.CommitPendingDelete -> commitPendingDelete()
        HistoryIntent.SyncNow -> startSync()
        HistoryIntent.AcknowledgeSyncIssues -> {
            viewModelScope.launch { acknowledgeSyncIssues() }
            Unit
        }
        HistoryIntent.MessageShown -> setState { copy(message = null) }
        HistoryIntent.Refresh -> setState { rebuild() }
    }

    private fun loadMore() {
        if (currentState.canLoadMore) limit.value += PAGE_SIZE
    }

    private fun deleteLater(transactionId: String) {
        commitPendingDelete() // mỗi lúc chỉ một dòng chờ hoàn tác
        setState { copy(pendingDeleteId = transactionId).rebuild() }
    }

    private fun commitPendingDelete() {
        val id = currentState.pendingDeleteId ?: return
        setState { copy(pendingDeleteId = null) } // vẫn ẩn: Flow sẽ bỏ dòng này khi xoá xong
        hiddenUntilGone += id
        setState { rebuild() }
        viewModelScope.launch {
            deleteTransaction(id).onFailure { error ->
                hiddenUntilGone -= id
                setState { copy(message = error.toLedgerUiText()).rebuild() }
            }
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

    private fun HistoryState.rebuild(canLoadMore: Boolean = this.canLoadMore): HistoryState {
        hiddenUntilGone.retainAll(latest.map { it.transaction.id }.toSet())
        val hidden = hiddenUntilGone + listOfNotNull(pendingDeleteId)
        val today = clock.today()
        val sections = latest
            .filterNot { it.transaction.id in hidden }
            .groupBy { it.transaction.occurredOn }
            .map { (date, items) ->
                DaySection(
                    key = date.toString(),
                    label = date.toRelativeLabel(today),
                    items = items.map { formatter.format(it.transaction, it.account, it.counterAccount, it.category, today = null) },
                )
            }
        return copy(isLoading = false, sections = sections, canLoadMore = canLoadMore)
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
