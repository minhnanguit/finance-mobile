package com.uit.finance.app.devtools

import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionType

/*
 * Bộ dữ liệu mẫu cho build debug. Ngày tính lùi từ hôm nay nên lúc nào cũng có giao dịch "Hôm nay",
 * "Hôm qua", tháng này và tháng trước. Cố ý kèm ca biên để soi UI: ví số dư âm, ví USD (đổi tiền tệ
 * phần "Tháng này" khi lướt thẻ), số tiền cỡ tỷ, tên người nhận/ghi chú dài, giao dịch nháp, không rõ giờ.
 * Số tiền là minor unit: VND không có phần lẻ, USD tính bằng cent.
 */

internal enum class DemoAccount(
    val title: String,
    val type: AccountType,
    val currency: String,
    val openingBalanceMinor: Long,
) {
    Cash("Tiền mặt", AccountType.CASH, "VND", 1_850_000),
    Bank("Vietcombank", AccountType.BANK, "VND", 24_500_000),
    Wallet("MoMo", AccountType.EWALLET, "VND", 420_000),
    CreditCard("Thẻ tín dụng VIB", AccountType.OTHER, "VND", -3_150_000),
    Usd("Wise USD", AccountType.BANK, "USD", 125_000),
}

/** Danh mục chọn theo `icon` của danh mục mặc định (khớp `CategoryIcons.kt`); không thấy thì lấy cái đầu cùng loại. */
internal enum class DemoCategory(val kind: CategoryKind, val iconKey: String) {
    Food(CategoryKind.EXPENSE, "restaurant"),
    Transport(CategoryKind.EXPENSE, "directions_car"),
    Shopping(CategoryKind.EXPENSE, "shopping_bag"),
    Bills(CategoryKind.EXPENSE, "receipt"),
    Housing(CategoryKind.EXPENSE, "home"),
    Entertainment(CategoryKind.EXPENSE, "movie"),
    Education(CategoryKind.EXPENSE, "school"),
    Salary(CategoryKind.INCOME, "payments"),
    Bonus(CategoryKind.INCOME, "redeem"),
    Investment(CategoryKind.INCOME, "trending_up"),
}

/** @param hour `null` = không rõ giờ (`occurredAt` để trống). */
internal data class DemoTransaction(
    val daysAgo: Int,
    val type: TransactionType,
    val account: DemoAccount,
    val amountMinor: Long,
    val category: DemoCategory? = null,
    val counterAccount: DemoAccount? = null,
    val payee: String? = null,
    val note: String? = null,
    val hour: Int? = 12,
    val draft: Boolean = false,
)

private fun expense(
    daysAgo: Int,
    account: DemoAccount,
    amountMinor: Long,
    category: DemoCategory,
    payee: String? = null,
    note: String? = null,
    hour: Int? = 12,
    draft: Boolean = false,
) = DemoTransaction(daysAgo, TransactionType.EXPENSE, account, amountMinor, category, null, payee, note, hour, draft)

private fun income(
    daysAgo: Int,
    account: DemoAccount,
    amountMinor: Long,
    category: DemoCategory,
    payee: String? = null,
    note: String? = null,
    hour: Int? = 9,
) = DemoTransaction(daysAgo, TransactionType.INCOME, account, amountMinor, category, null, payee, note, hour)

private fun transfer(daysAgo: Int, from: DemoAccount, to: DemoAccount, amountMinor: Long, note: String? = null) =
    DemoTransaction(daysAgo, TransactionType.TRANSFER, from, amountMinor, counterAccount = to, note = note, hour = 20)

internal val DemoTransactions: List<DemoTransaction> = listOf(
    // ---- Gần đây: lấp đầy "Giao dịch gần đây" trên trang chủ ----
    expense(0, DemoAccount.Wallet, 45_000, DemoCategory.Food, payee = "Highlands Coffee", hour = 8),
    expense(0, DemoAccount.Cash, 35_000, DemoCategory.Food, payee = "Cơm tấm Ba Ghiền", hour = 12),
    expense(0, DemoAccount.Bank, 389_000, DemoCategory.Shopping, payee = "Shopee", note = "Nháp: chưa chắc giữ đơn", hour = 21, draft = true),
    expense(1, DemoAccount.Wallet, 62_000, DemoCategory.Transport, payee = "Grab", hour = 18),
    expense(
        1,
        DemoAccount.CreditCard,
        1_286_500,
        DemoCategory.Shopping,
        payee = "Siêu thị Co.opmart Nguyễn Đình Chiểu – Quận 3, TP. Hồ Chí Minh (chi nhánh trung tâm)",
        note = "Đi chợ cả tuần: rau, thịt, sữa, đồ dùng nhà tắm, nước giặt, bột giặt, khăn giấy và vài món lặt vặt khác cho cả nhà.",
        hour = 19,
    ),
    transfer(2, DemoAccount.Bank, DemoAccount.Wallet, 500_000, note = "Nạp MoMo"),
    expense(2, DemoAccount.Usd, 1_549, DemoCategory.Entertainment, payee = "Netflix", hour = null),
    expense(3, DemoAccount.Bank, 1_120_000, DemoCategory.Bills, payee = "EVN HCMC", note = "Tiền điện tháng trước"),
    expense(4, DemoAccount.Cash, 150_000, DemoCategory.Entertainment, payee = "CGV Vincom", hour = 20),
    income(5, DemoAccount.Usd, 45_000, DemoCategory.Investment, payee = "Upwork", note = "Freelance landing page"),
    expense(6, DemoAccount.Wallet, 99_000, DemoCategory.Food, payee = "ShopeeFood"),
    transfer(7, DemoAccount.Bank, DemoAccount.CreditCard, 3_000_000, note = "Trả thẻ tín dụng"),
    expense(8, DemoAccount.Bank, 6_500_000, DemoCategory.Housing, payee = "Chủ nhà", note = "Tiền thuê nhà", hour = null),
    expense(9, DemoAccount.Cash, 280_000, DemoCategory.Transport, payee = "Petrolimex"),
    income(10, DemoAccount.Bank, 18_000_000, DemoCategory.Salary, payee = "Công ty TNHH ABC", note = "Lương tháng"),
    expense(11, DemoAccount.CreditCard, 2_490_000, DemoCategory.Education, payee = "Udemy", note = "Khoá Kotlin Multiplatform"),
    expense(12, DemoAccount.Wallet, 230_000, DemoCategory.Bills, payee = "Viettel", note = "Cước internet"),
    transfer(13, DemoAccount.Bank, DemoAccount.Cash, 2_000_000, note = "Rút tiền mặt"),
    expense(14, DemoAccount.Cash, 75_000, DemoCategory.Food, payee = "Phở Hòa"),
    income(15, DemoAccount.Bank, 1_250_000_000, DemoCategory.Bonus, payee = "Bán xe", note = "Số tiền cỡ tỷ để thử co giãn chữ"),
    // ---- Tháng trước: dữ liệu cho báo cáo / so sánh tháng ----
    expense(20, DemoAccount.Wallet, 54_000, DemoCategory.Food, payee = "Phúc Long"),
    expense(24, DemoAccount.Cash, 120_000, DemoCategory.Transport, payee = "Be"),
    expense(28, DemoAccount.CreditCard, 799_000, DemoCategory.Shopping, payee = "Uniqlo"),
    expense(31, DemoAccount.Bank, 1_050_000, DemoCategory.Bills, payee = "EVN HCMC"),
    income(35, DemoAccount.Bank, 18_000_000, DemoCategory.Salary, payee = "Công ty TNHH ABC", note = "Lương tháng"),
    expense(38, DemoAccount.Bank, 6_500_000, DemoCategory.Housing, payee = "Chủ nhà", note = "Tiền thuê nhà", hour = null),
    expense(42, DemoAccount.Usd, 1_549, DemoCategory.Entertainment, payee = "Netflix", hour = null),
    income(45, DemoAccount.Wallet, 200_000, DemoCategory.Bonus, payee = "MoMo", note = "Hoàn tiền khuyến mãi"),
    expense(50, DemoAccount.Cash, 310_000, DemoCategory.Food, payee = "Lẩu Manwah"),
    expense(56, DemoAccount.Wallet, 85_000, DemoCategory.Transport, payee = "Grab"),
)
