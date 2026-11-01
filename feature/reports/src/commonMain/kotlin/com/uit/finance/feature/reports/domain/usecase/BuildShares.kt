package com.uit.finance.feature.reports.domain.usecase

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryTotal
import com.uit.finance.feature.reports.domain.model.CategoryShare
import com.uit.finance.feature.reports.domain.model.MAX_PERCENT
import com.uit.finance.feature.reports.domain.model.floorTenth
import com.uit.finance.feature.reports.domain.model.saturatingPlus

/**
 * Đổi tổng theo danh mục của một tiền tệ thành các nhóm có % (R3, R5):
 * - id không có trên máy (danh mục chưa sync về) gộp vào "Chưa phân loại" cùng nhóm không có danh mục;
 * - mẫu số = tổng các nhóm này cộng bằng Double (không kẹp ở biên Long như số tiền, để các tỷ lệ luôn cộng lại đúng 1),
 *   % làm tròn xuống một chữ số lẻ: tổng các % không vượt 100;
 * - lớn trước; bằng nhau thì theo tên cho thứ tự ổn định.
 */
internal fun buildShares(
    totals: List<CategoryTotal>,
    currency: String,
    categories: List<Category>,
    groupedByParent: Boolean,
): List<CategoryShare> {
    val byId = categories.associateBy { it.id }
    val parentsWithChildren = categories.mapNotNullTo(mutableSetOf()) { it.parentId }
    val amounts = LinkedHashMap<String?, Long>()
    totals.filter { it.total.currency == currency && it.total.amountMinor != 0L }.forEach { total ->
        val key = total.categoryId?.takeIf { it in byId }
        amounts[key] = (amounts[key] ?: 0L).saturatingPlus(total.total.amountMinor)
    }
    val denominator = amounts.values.sumOf { it.toDouble() }
    return amounts.map { (id, amount) ->
        val percent = if (denominator > 0.0) amount.toDouble() / denominator * MAX_PERCENT else 0.0
        CategoryShare(
            category = id?.let(byId::get),
            total = Money(amount, currency),
            percent = floorTenth(percent.coerceIn(0.0, MAX_PERCENT)),
            hasChildren = groupedByParent && id != null && id in parentsWithChildren,
        )
    }.sortedWith(compareByDescending<CategoryShare> { it.total.amountMinor }.thenBy { it.category?.name ?: "￿" })
}

/**
 * Ví cần lọc khi mở lịch sử giao dịch từ báo cáo [currency] (giao dịch luôn mang tiền tệ của ví). Gồm cả ví đã lưu trữ.
 * `null` khi mọi ví cùng tiền tệ (không cần lọc) hoặc không ví nào trên máy có tiền tệ đó (không lọc được).
 */
internal fun accountIdsFor(currency: String, accounts: List<Account>): Set<String>? {
    val matching = accounts.filter { it.currency == currency }
    return if (matching.isEmpty() || matching.size == accounts.size) null else matching.mapTo(mutableSetOf()) { it.id }
}
