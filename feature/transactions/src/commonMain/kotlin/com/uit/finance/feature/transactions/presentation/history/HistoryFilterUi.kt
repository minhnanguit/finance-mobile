package com.uit.finance.feature.transactions.presentation.history

import com.uit.finance.core.common.text.foldForSearch
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionTotals
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.ui.ledger.shortLabel
import com.uit.finance.feature.transactions.domain.model.DatePreset
import com.uit.finance.feature.transactions.domain.model.HistoryFilter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/*
 * Bộ lọc → chữ trên màn hình. Thuần Kotlin để test không cần Compose; ViewModel gọi, màn hình chỉ vẽ.
 */

internal fun DatePreset.label(): String = when (this) {
    DatePreset.ALL -> "Tất cả"
    DatePreset.THIS_MONTH -> "Tháng này"
    DatePreset.LAST_MONTH -> "Tháng trước"
    DatePreset.LAST_30_DAYS -> "30 ngày qua"
    DatePreset.THIS_YEAR -> "Năm nay"
    DatePreset.CUSTOM -> "Tuỳ chọn"
}

/** Chi trước (hay dùng nhất), rồi Thu, rồi Chuyển: cùng thứ tự với form giao dịch. */
internal val FILTER_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

/** "14/11/2023". */
internal fun LocalDate.toShortLabel(): String =
    "${day.toString().padStart(2, '0')}/${month.number.toString().padStart(2, '0')}/$year"

/** Một chip cho mỗi điều kiện đang bật, theo thứ tự của sheet lọc. Tên ví/danh mục tra từ [accounts], [categories]. */
internal fun HistoryFilter.chips(accounts: List<Account>, categories: List<Category>): List<FilterChipUi> = buildList {
    if (hasDateRange) add(FilterChipUi(FilterChipKey.Date, dateLabel()))
    if (types.isNotEmpty()) {
        add(FilterChipUi(FilterChipKey.Types, FILTER_TYPES.filter { it in types }.joinToString(", ") { it.shortLabel() }))
    }
    if (accountIds.isNotEmpty()) {
        val name = accountIds.singleOrNull()?.let { id -> accounts.firstOrNull { it.id == id }?.name }
        add(FilterChipUi(FilterChipKey.Accounts, name ?: "${accountIds.size} ví"))
    }
    if (categoryIds.isNotEmpty()) {
        val name = categoryIds.singleOrNull()?.let { id -> categories.firstOrNull { it.id == id }?.name }
        add(FilterChipUi(FilterChipKey.Categories, name ?: "${categoryIds.size} danh mục"))
    }
    if (draftsOnly) add(FilterChipUi(FilterChipKey.Status, "Nháp"))
}

private fun HistoryFilter.dateLabel(): String {
    if (datePreset != DatePreset.CUSTOM) return datePreset.label()
    val from = customFrom
    val to = customTo
    return when {
        from != null && to != null -> if (from == to) from.toShortLabel() else "${from.toShortLabel()} – ${to.toShortLabel()}"
        from != null -> "Từ ${from.toShortLabel()}"
        to != null -> "Đến ${to.toShortLabel()}"
        else -> datePreset.label()
    }
}

/** Gỡ đúng điều kiện của chip [key], giữ nguyên các điều kiện khác. */
internal fun HistoryFilter.without(key: FilterChipKey): HistoryFilter = when (key) {
    FilterChipKey.Date -> copy(datePreset = DatePreset.ALL, customFrom = null, customTo = null)
    FilterChipKey.Types -> copy(types = emptySet())
    FilterChipKey.Accounts -> copy(accountIds = emptySet())
    FilterChipKey.Categories -> copy(categoryIds = emptySet())
    FilterChipKey.Status -> copy(draftsOnly = false)
}

internal fun TransactionTotals.toUi(money: MoneyFormatter): HistoryTotalsUi {
    val summary = buildString {
        append("$count giao dịch")
        if (draftCount > 0) append(" (gồm $draftCount nháp, không tính vào tổng)")
    }
    val currencies = cashFlow.currencies.sorted().map { currency ->
        CurrencyTotalsUi(
            currency = currency,
            income = cashFlow.income(currency).takeIf { it.amountMinor != 0L }?.let { money.format(it).amount },
            expense = cashFlow.expense(currency).takeIf { it.amountMinor != 0L }?.let { money.format(it).amount },
        )
    }
    return HistoryTotalsUi(summary = summary, currencies = currencies)
}

/** Mọi ví chưa xoá, kể cả ví đã lưu trữ (giao dịch cũ vẫn thuộc về chúng). */
internal fun List<Account>.toAccountFilterOptions(): List<AccountFilterOptionUi> =
    sortedWith(compareBy<Account> { it.archived }.thenBy { it.sortOrder })
        .map { AccountFilterOptionUi(id = it.id, name = it.name, archived = it.archived) }

/**
 * Danh mục chi trước, rồi thu; mỗi danh mục cha theo sau là các con của nó. Xếp theo tên đã bỏ dấu: so chuỗi thường
 * thì "Ăn uống" rơi xuống sau "Sức khoẻ", vì "Ă" đứng sau mọi chữ Latin trong bảng mã.
 */
internal fun List<Category>.toCategoryFilterOptions(): List<CategoryFilterOptionUi> {
    val byId = associateBy { it.id }
    val childrenOf = filter { it.parentId != null && it.parentId in byId }.groupBy { it.parentId }
    val roots = filter { it.parentId == null || it.parentId !in byId }
        .sortedWith(compareBy<Category>({ it.kind != CategoryKind.EXPENSE }, { it.name.foldForSearch() }))
    return roots.flatMap { root -> listOf(root) + childrenOf[root.id].orEmpty().sortedBy { it.name.foldForSearch() } }
        .map { CategoryFilterOptionUi(id = it.id, name = it.name, kind = it.kind, iconKey = it.icon, colorHex = it.color) }
}
