package com.uit.finance.core.ledger.data.local

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.text.foldForSearch
import com.uit.finance.core.database.LedgerSearchQueries
import com.uit.finance.core.database.SelectFilteredTotals
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionTotals
import com.uit.finance.core.ledger.domain.model.TransactionType
import kotlinx.datetime.LocalDate

/**
 * [TransactionFilter] dịch sang tham số của `LedgerSearch.sq`. Phụ thuộc cây danh mục (lọc cha gồm con, tìm theo
 * tên danh mục: F6), nên được tính lại mỗi khi danh mục đổi. List đã sắp xếp: hai lần tính ra cùng kết quả thì
 * bằng nhau, không chạy lại câu SQL.
 *
 * List `null` = không lọc theo điều kiện đó (F3).
 */
internal data class TransactionSearch(
    val fromDate: String,
    val toDate: String,
    val types: List<String>?,
    val accountIds: List<String>?,
    val categoryIds: List<String>?,
    val status: String?,
    /** Đã [foldForSearch] và escape cho `LIKE` (F7); rỗng = không tìm. */
    val likeQuery: String,
    /** Danh mục có tên khớp từ khoá, gồm cả con của chúng. */
    val textCategoryIds: List<String>,
) {
    /** Không in từ khoá (ADR-006 B8). */
    override fun toString(): String = "TransactionSearch(fromDate=$fromDate, toDate=$toDate, types=$types, status=$status)"

    companion object {
        /** "Tất cả" vẫn là một khoảng ngày thật, để câu SQL luôn đi theo index ngày (F2). */
        private val MIN_DATE = LocalDate(1, 1, 1)
        private val MAX_DATE = LocalDate(9999, 12, 31)

        /** [categories]: danh mục chưa xoá, kể cả đã lưu trữ. */
        fun of(filter: TransactionFilter, categories: List<Category>): TransactionSearch {
            val normalized = filter.normalized()
            val query = normalized.query?.foldForSearch().orEmpty()
            val textCategoryIds = if (query.isEmpty()) {
                emptySet()
            } else {
                categories.withChildren(categories.filter { query in it.name.foldForSearch() }.mapTo(HashSet()) { it.id })
            }
            return TransactionSearch(
                fromDate = (normalized.from ?: MIN_DATE).toString(),
                toDate = (normalized.to ?: MAX_DATE).toString(),
                types = normalized.types?.map { it.name }?.sorted(),
                accountIds = normalized.accountIds?.sorted(),
                categoryIds = normalized.categoryIds?.let { categories.withChildren(it) }?.sorted(),
                status = normalized.status?.name,
                likeQuery = query.escapeForLike(),
                textCategoryIds = textCategoryIds.sorted(),
            )
        }

        /** Danh mục tối đa 2 cấp (ADR-005 §2), nên một lượt là đủ. */
        private fun List<Category>.withChildren(ids: Set<String>): Set<String> =
            ids + filter { it.parentId in ids }.map { it.id }

        /** `\` trước, rồi `%` và `_`; câu SQL khai `ESCAPE '\'`. Gõ "50%" không thành wildcard. */
        private fun String.escapeForLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

internal fun LedgerSearchQueries.filtered(search: TransactionSearch, limit: Long) = selectFiltered(
    fromDate = search.fromDate,
    toDate = search.toDate,
    allTypes = search.types.allFlag(),
    types = search.types.orEmpty(),
    allAccounts = search.accountIds.allFlag(),
    accountIds = search.accountIds.orEmpty(),
    allCategories = search.categoryIds.allFlag(),
    categoryIds = search.categoryIds.orEmpty(),
    anyStatus = search.status.allFlag(),
    status = search.status.orEmpty(),
    query = search.likeQuery,
    textCategoryIds = search.textCategoryIds,
    limit = limit,
)

internal fun LedgerSearchQueries.filteredTotals(search: TransactionSearch) = selectFilteredTotals(
    fromDate = search.fromDate,
    toDate = search.toDate,
    allTypes = search.types.allFlag(),
    types = search.types.orEmpty(),
    allAccounts = search.accountIds.allFlag(),
    accountIds = search.accountIds.orEmpty(),
    allCategories = search.categoryIds.allFlag(),
    categoryIds = search.categoryIds.orEmpty(),
    anyStatus = search.status.allFlag(),
    status = search.status.orEmpty(),
    query = search.likeQuery,
    textCategoryIds = search.textCategoryIds,
)

/** Mỗi dòng là một nhóm (loại, trạng thái, tiền tệ): đếm mọi nhóm, chỉ cộng tiền nhóm thu/chi `CONFIRMED` (F10). */
internal fun List<SelectFilteredTotals>.toTotals(): TransactionTotals {
    val confirmed = filter { it.status == TransactionStatus.CONFIRMED.name }
    fun totalsOf(type: TransactionType) = confirmed.filter { it.type == type.name }.map { Money(it.total_minor, it.currency) }
    return TransactionTotals(
        count = sumOf { it.row_count }.toInt(),
        draftCount = filter { it.status == TransactionStatus.DRAFT.name }.sumOf { it.row_count }.toInt(),
        cashFlow = CashFlow(income = totalsOf(TransactionType.INCOME), expense = totalsOf(TransactionType.EXPENSE)),
    )
}

/** Cờ "tất cả" của câu SQL (F3): `null` = không lọc. */
private fun Any?.allFlag(): Long = if (this == null) 1L else 0L
