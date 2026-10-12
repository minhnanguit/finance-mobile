package com.uit.finance.feature.accounts.domain.model

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountType

/** Ví kèm số dư hiện tại (tính từ lịch sử, D4). */
data class AccountWithBalance(val account: Account, val balance: Money)

/**
 * Màn danh sách ví: ví đang dùng theo thứ tự user sắp, ví đã lưu trữ tách riêng, tổng theo từng tiền tệ (chỉ
 * ví đang dùng, không cộng lẫn tiền tệ, ADR-001).
 */
data class AccountsOverview(
    val active: List<AccountWithBalance>,
    val archived: List<AccountWithBalance>,
    val totals: List<Money>,
)

/** Dữ liệu form tạo/sửa ví. `id == null` là tạo mới. */
data class AccountInput(
    val id: String?,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalanceMinor: Long,
)

enum class MoveDirection { Up, Down }
