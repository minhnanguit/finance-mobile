package com.uit.finance.core.ledger.domain.validation

import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.model.TransactionType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Bản sao phía máy của giới hạn backend (ADR-005 §4). Kiểm sớm để không phải chờ server trả
 * `REJECTED`; server vẫn là người quyết định cuối.
 */
object LedgerLimits {
    const val MAX_AMOUNT_MINOR: Long = 1_000_000_000_000_000L
    const val MAX_NAME_LENGTH = 50
    const val MAX_PAYEE_LENGTH = 100
    const val MAX_NOTE_LENGTH = 500
    const val MAX_ACCOUNTS = 50
    const val MAX_CATEGORIES = 300
    val EARLIEST_DATE = LocalDate(2000, 1, 1)
    val MAX_AHEAD = DatePeriod(years = 1)
}

/** Kiểm từng field, không cần DB. Trả danh sách lỗi; rỗng là hợp lệ. */
object LedgerRules {

    private val CURRENCY = Regex("[A-Z]{3}")
    private val ICON = Regex("[a-z0-9_]{1,50}")
    private val COLOR = Regex("#[0-9A-Fa-f]{6}")

    /** Tên ví/danh mục: cắt khoảng trắng 2 đầu, 1–50 ký tự (đếm theo ký tự thật, emoji là 1). */
    fun name(value: String, field: String = "name"): List<FieldError> {
        val length = value.trim().characterCount()
        return if (length in 1..LedgerLimits.MAX_NAME_LENGTH) emptyList() else invalid(field)
    }

    fun currency(value: String): List<FieldError> = if (CURRENCY.matches(value)) emptyList() else invalid("currency")

    fun openingBalance(minor: Long): List<FieldError> =
        if (minor in -LedgerLimits.MAX_AMOUNT_MINOR..LedgerLimits.MAX_AMOUNT_MINOR) emptyList() else invalid("openingBalanceMinor")

    fun icon(value: String?): List<FieldError> = if (value == null || ICON.matches(value)) emptyList() else invalid("icon")

    fun color(value: String?): List<FieldError> = if (value == null || COLOR.matches(value)) emptyList() else invalid("color")

    fun amount(minor: Long): List<FieldError> =
        if (minor in 1..LedgerLimits.MAX_AMOUNT_MINOR) emptyList() else invalid("amountMinor")

    fun optionalText(value: String?, max: Int, field: String): List<FieldError> =
        if (value == null || value.trim().characterCount() <= max) emptyList() else invalid(field)

    fun occurredOn(date: LocalDate, today: LocalDate): List<FieldError> =
        if (date >= LedgerLimits.EARLIEST_DATE && date <= today.plus(LedgerLimits.MAX_AHEAD)) emptyList() else invalid("occurredOn")

    /** Chuyển tiền: có ví đến khác ví đi, không danh mục. Thu/chi: có danh mục, không ví đến (D2). */
    fun shape(type: TransactionType, accountId: String, counterAccountId: String?, categoryId: String?): List<FieldError> =
        if (type == TransactionType.TRANSFER) {
            when {
                counterAccountId == null || counterAccountId == accountId ->
                    listOf(FieldError("counterAccountId", LedgerErrorCode.INVALID_TRANSFER))
                categoryId != null -> invalid("categoryId")
                else -> emptyList()
            }
        } else {
            when {
                counterAccountId != null -> invalid("counterAccountId")
                categoryId == null -> invalid("categoryId")
                else -> emptyList()
            }
        }

    private fun invalid(field: String) = listOf(FieldError(field, LedgerErrorCode.INVALID_FIELD))
}

/** Số ký tự thật (code point), khớp cách backend và Postgres đếm. */
internal fun String.characterCount(): Int = count { !it.isLowSurrogate() }

/** Rỗng hoặc chỉ khoảng trắng thì coi như không có, giống backend. */
fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
