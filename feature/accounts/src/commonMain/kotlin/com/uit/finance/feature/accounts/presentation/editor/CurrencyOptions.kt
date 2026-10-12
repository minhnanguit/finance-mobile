package com.uit.finance.feature.accounts.presentation.editor

/**
 * Tiền tệ cho chọn khi tạo ví: VND trước, rồi các tiền tệ người Việt hay giữ. Thêm tiền tệ = thêm một dòng;
 * format và số chữ số lẻ tự theo `Currencies` (ISO 4217).
 */
internal data class CurrencyOption(val code: String, val name: String)

internal val CurrencyOptions: List<CurrencyOption> = listOf(
    CurrencyOption("VND", "Đồng Việt Nam"),
    CurrencyOption("USD", "Đô la Mỹ"),
    CurrencyOption("EUR", "Euro"),
    CurrencyOption("JPY", "Yên Nhật"),
    CurrencyOption("KRW", "Won Hàn Quốc"),
    CurrencyOption("CNY", "Nhân dân tệ"),
    CurrencyOption("SGD", "Đô la Singapore"),
    CurrencyOption("THB", "Baht Thái"),
    CurrencyOption("AUD", "Đô la Úc"),
    CurrencyOption("GBP", "Bảng Anh"),
)
