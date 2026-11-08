package com.uit.finance.core.ledger.domain.model

import kotlinx.datetime.LocalDate

/**
 * Điều kiện lọc lịch sử giao dịch (docs/SEARCH-PLAN.md F1). Một model dùng chung cho danh sách, dòng tổng và khi
 * mở lịch sử từ màn khác. Các điều kiện ghép bằng AND; `null` = không lọc theo điều kiện đó.
 *
 * @param query khớp người nhận, ghi chú hoặc tên danh mục; không phân biệt hoa thường, không phân biệt dấu
 * @param from ngày đầu, gồm cả ngày đó
 * @param to ngày cuối, gồm cả ngày đó
 * @param accountIds chuyển tiền khớp cả ví gửi lẫn ví nhận; gồm được cả ví đã lưu trữ
 * @param categoryIds chọn danh mục cha là gồm luôn các danh mục con
 */
data class TransactionFilter(
    val query: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val types: Set<TransactionType>? = null,
    val accountIds: Set<String>? = null,
    val categoryIds: Set<String>? = null,
    val status: TransactionStatus? = null,
) {
    /**
     * Dạng chuẩn (F3, F7): tập rỗng và từ khoá trống đều thành `null`, tức "tất cả"; từ khoá bỏ khoảng trắng hai
     * đầu và cắt còn [MAX_QUERY_LENGTH] ký tự. Repository luôn lọc theo dạng này.
     */
    fun normalized(): TransactionFilter = copy(
        query = query?.trim()?.take(MAX_QUERY_LENGTH)?.trimEnd()?.ifEmpty { null },
        types = types?.ifEmpty { null },
        accountIds = accountIds?.ifEmpty { null },
        categoryIds = categoryIds?.ifEmpty { null },
    )

    /** Không in từ khoá (ADR-006 B8): người dùng hay gõ đúng nội dung ghi chú, người nhận. */
    override fun toString(): String =
        "TransactionFilter(query=${query?.let { "<redacted>" }}, from=$from, to=$to, types=$types, " +
            "accountIds=$accountIds, categoryIds=$categoryIds, status=$status)"

    companion object {
        const val MAX_QUERY_LENGTH = 100
        val ALL = TransactionFilter()
    }
}

/**
 * Dòng tổng của kết quả lọc (F10).
 *
 * @param count mọi dòng khớp, gồm cả nháp và chuyển tiền: bằng số dòng danh sách hiện khi kéo hết
 * @param draftCount số nháp trong [count]; nháp không vào [cashFlow]
 * @param cashFlow thu/chi `CONFIRMED`, bỏ `TRANSFER`, tách theo tiền tệ: cùng cách tính với
 *   `BalanceRepository.observeCashFlow`, nên chỉ lọc theo ngày thì hai số bằng nhau
 */
data class TransactionTotals(val count: Int, val draftCount: Int, val cashFlow: CashFlow) {
    companion object {
        val EMPTY = TransactionTotals(count = 0, draftCount = 0, cashFlow = CashFlow.EMPTY)
    }
}
