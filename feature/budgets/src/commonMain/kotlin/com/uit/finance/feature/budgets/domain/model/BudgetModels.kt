package com.uit.finance.feature.budgets.domain.model

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import kotlinx.datetime.YearMonth

/** Màu của thanh: vàng khi đã dùng ≥ 80%, đỏ khi ≥ 100% (ADR-007 G9). */
enum class BudgetTone { Normal, Warning, Over }

/**
 * Một ngân sách trong một tháng.
 *
 * @param category `null` khi danh mục chưa sync tới hoặc vừa bị xoá ở máy khác (tombstone ngân sách sẽ tới sau)
 * @param remaining âm khi đã vượt
 * @param percent chỉ để hiển thị: làm tròn xuống, tối đa 999 (G10)
 * @param ratio đã chi / ngân sách, để sắp xếp "gần vượt nhất lên đầu"
 * @param dailyAllowance mỗi ngày còn được chi, chỉ có ở tháng hiện tại khi còn lại > 0 (G11)
 */
data class BudgetProgress(
    val budget: Budget,
    val category: Category?,
    val spent: Money,
    val remaining: Money,
    val percent: Int,
    val ratio: Double,
    val tone: BudgetTone,
    val dailyAllowance: Money?,
) {
    override fun toString(): String = "BudgetProgress(budget=${budget.id}, tone=$tone)"
}

/** Thẻ tổng của một tiền tệ (không cộng chéo tiền tệ, ADR-001). Cộng bão hoà ở biên Long (G10). */
data class BudgetTotal(val budgeted: Money, val spent: Money, val remaining: Money) {
    val currency: String get() = budgeted.currency
}

data class BudgetOverview(
    val month: YearMonth,
    /** Gần vượt nhất lên đầu. */
    val items: List<BudgetProgress>,
    val totals: List<BudgetTotal>,
)

/**
 * Dữ liệu cho form ngân sách.
 *
 * @param currencies tiền tệ của các ví đang dùng, theo thứ tự ví; chưa có ví thì là VND
 * @param categories danh mục chi cấp 1 chưa lưu trữ (G2, G13)
 */
data class BudgetFormData(
    val currencies: List<String>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val allCategories: List<Category>,
) {
    /** Tiền tệ của ví đang dùng đầu tiên (G15). */
    val defaultCurrency: String get() = currencies.first()

    /** Danh mục chọn được để tạo mới: chưa có ngân sách ở [currency] (G3, G15). */
    fun availableCategories(currency: String): List<Category> {
        val taken = budgets.filter { it.currency == currency }.map { it.categoryId }.toSet()
        return categories.filterNot { it.id in taken }
    }

    companion object {
        const val FALLBACK_CURRENCY = "VND"

        fun from(accounts: List<Account>, categories: List<Category>, budgets: List<Budget>) = BudgetFormData(
            currencies = accounts.filterNot { it.archived }
                .sortedBy { it.sortOrder }
                .map { it.currency }
                .distinct()
                .ifEmpty { listOf(FALLBACK_CURRENCY) },
            categories = categories
                .filter { it.kind == CategoryKind.EXPENSE && it.parentId == null && !it.archived }
                .sortedBy { it.name.lowercase() },
            budgets = budgets,
            allCategories = categories,
        )
    }
}

/** Dữ liệu form khi lưu. `id == null` là tạo mới; khi sửa chỉ [amountMinor] được dùng (G8). */
data class BudgetInput(
    val id: String?,
    val categoryId: String,
    val currency: String,
    val amountMinor: Long,
    val startMonth: YearMonth,
) {
    override fun toString(): String = "BudgetInput(id=$id, categoryId=$categoryId, currency=$currency)"
}
