package com.uit.finance.feature.transactions.presentation.history

import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.sync.domain.model.SyncOverview
import com.uit.finance.core.ui.transaction.TransactionItemUi
import com.uit.finance.feature.transactions.domain.model.DatePreset
import com.uit.finance.feature.transactions.domain.model.HistoryFilter
import kotlinx.datetime.LocalDate

/**
 * Tham số mở màn (qua Koin `parametersOf`). Tab Giao dịch dùng mặc định; mở từ màn khác (báo cáo, ngân sách) thì có
 * bộ lọc điền sẵn và nút quay lại (F11).
 */
data class HistoryArgs(val initialFilter: HistoryFilter = HistoryFilter(), val showBack: Boolean = false)

data class HistoryState(
    val isLoading: Boolean = true,
    val sections: List<DaySection> = emptyList(),
    /** Đã hiện đủ số dòng của cửa sổ hiện tại: cuộn tới cuối thì nạp thêm. */
    val canLoadMore: Boolean = false,
    val sync: SyncOverview = SyncOverview.IDLE,
    val isRefreshing: Boolean = false,
    /** Giao dịch vừa vuốt xoá, đang chờ hết thời gian hoàn tác (đã ẩn khỏi danh sách). */
    val pendingDeleteId: String? = null,
    val message: UiText? = null,
    val showBack: Boolean = false,
    /** Chữ đang gõ. Danh sách chỉ tìm theo nó sau khi ngừng gõ 300 ms. */
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter(),
    val chips: List<FilterChipUi> = emptyList(),
    /** Danh sách đang hiện là kết quả lọc (có từ khoá hoặc điều kiện): hiện dòng tổng, trống thì "Không tìm thấy". */
    val isFiltered: Boolean = false,
    val totals: HistoryTotalsUi? = null,
    val accountOptions: List<AccountFilterOptionUi> = emptyList(),
    val categoryOptions: List<CategoryFilterOptionUi> = emptyList(),
    val isFilterSheetOpen: Boolean = false,
    val datePicker: DatePickerUi? = null,
)

/** Các giao dịch cùng một ngày, nhãn "Hôm nay" / "Hôm qua" / "9 thg 10". */
data class DaySection(val key: String, val label: String, val items: List<TransactionItemUi>)

/** Mỗi điều kiện đang bật là một chip; gỡ chip là gỡ cả điều kiện đó. */
enum class FilterChipKey { Date, Types, Accounts, Categories, Status }

data class FilterChipUi(val key: FilterChipKey, val label: String)

/**
 * Dòng tổng (F10): [summary] = "N giao dịch", kèm "(gồm k nháp, không tính vào tổng)" khi có nháp. Thu/chi mỗi tiền
 * tệ một dòng, không bao giờ cộng lẫn tiền tệ; vế bằng 0 thì `null` (không hiện "Thu 0" khi chỉ lọc khoản chi).
 */
data class HistoryTotalsUi(val summary: String, val currencies: List<CurrencyTotalsUi>)

data class CurrencyTotalsUi(val currency: String, val income: String?, val expense: String?)

data class AccountFilterOptionUi(val id: String, val name: String, val archived: Boolean)

/** Danh mục con đứng ngay sau danh mục cha của nó. */
data class CategoryFilterOptionUi(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String?,
    val colorHex: String?,
)

enum class DateBound { From, To }

/** Đang chọn một đầu của khoảng ngày "Tuỳ chọn". Ngày ngoài [range] bị khoá, nên "từ" không bao giờ sau "đến". */
data class DatePickerUi(val bound: DateBound, val selected: LocalDate, val range: ClosedRange<LocalDate>)

sealed interface HistoryIntent {
    data object LoadMore : HistoryIntent
    data object AddClicked : HistoryIntent
    data class ItemClicked(val transactionId: String) : HistoryIntent
    data object CategoriesClicked : HistoryIntent
    data class DeleteSwiped(val transactionId: String) : HistoryIntent
    data object UndoDelete : HistoryIntent

    /** Hết thời gian hoàn tác hoặc rời màn hình: xoá thật. */
    data object CommitPendingDelete : HistoryIntent
    data object SyncNow : HistoryIntent
    data object AcknowledgeSyncIssues : HistoryIntent
    data object MessageShown : HistoryIntent

    /** Màn hình resume: đọc lại "hôm nay" (nhãn ngày, preset "Tháng này"). */
    data object Refresh : HistoryIntent
    data object BackClicked : HistoryIntent

    // ---- Tìm kiếm + lọc ----
    data class QueryChanged(val value: String) : HistoryIntent
    data object FilterClicked : HistoryIntent
    data object FilterSheetDismissed : HistoryIntent
    data class DatePresetSelected(val preset: DatePreset) : HistoryIntent
    data class DatePickerOpened(val bound: DateBound) : HistoryIntent
    data object DatePickerDismissed : HistoryIntent
    data class CustomDateSelected(val bound: DateBound, val date: LocalDate) : HistoryIntent
    data class TypeToggled(val type: TransactionType) : HistoryIntent
    data class AccountToggled(val accountId: String) : HistoryIntent
    data class CategoryToggled(val categoryId: String) : HistoryIntent
    data class DraftsOnlyChanged(val draftsOnly: Boolean) : HistoryIntent
    data class ChipRemoved(val key: FilterChipKey) : HistoryIntent

    /** "Xoá lọc": bỏ cả từ khoá lẫn mọi điều kiện. */
    data object ClearFilters : HistoryIntent
}

sealed interface HistoryEffect {
    data class OpenEditor(val transactionId: String?) : HistoryEffect
    data object OpenCategories : HistoryEffect
    data object Close : HistoryEffect
}
