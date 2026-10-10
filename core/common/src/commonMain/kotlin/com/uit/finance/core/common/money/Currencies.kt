package com.uit.finance.core.common.money

/**
 * Số chữ số lẻ theo ISO 4217 (bảng CLDR `supplementalData.xml`, mục `currencyData`). Dùng khi đổi giữa
 * `amountMinor` và số hiển thị (VND: 1 đồng = 1 minor unit; USD: 100 cent = 1 đô). Chuẩn ISO: mọi tiền tệ
 * không liệt kê đều có 2 chữ số lẻ, nên bảng chỉ cần giữ các ngoại lệ (0, 3 và 4 chữ số).
 */
object Currencies {
    const val DEFAULT_MINOR_DIGITS = 2

    private val zeroDigits = setOf(
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "UYI",
        "VND", "VUV", "XAF", "XOF", "XPF",
    )
    private val threeDigits = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")
    private val fourDigits = setOf("CLF", "UYW")

    fun minorDigits(code: String): Int = when (code) {
        in zeroDigits -> 0
        in threeDigits -> 3
        in fourDigits -> 4
        else -> DEFAULT_MINOR_DIGITS
    }
}
