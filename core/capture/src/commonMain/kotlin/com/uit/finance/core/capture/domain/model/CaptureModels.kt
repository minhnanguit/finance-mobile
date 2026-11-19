@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Ngân hàng được hỗ trợ. Mỗi giá trị khai báo package app ngân hàng (thông báo đẩy) và tên người gửi SMS (thông báo
 * của app Tin nhắn). **Package và tên người gửi phải kiểm lại bằng mẫu thật ở Phase 0** (`adb shell dumpsys
 * notification --noredact`); giá trị dưới đây là điểm bắt đầu, sai thì chỉ làm thông báo bị bỏ qua, không ghi nhầm.
 */
enum class BankId(
    val displayName: String,
    val packages: Set<String>,
    val smsSenders: Set<String>,
    /** Chỉ có ở bản debug: cho phép `adb shell cmd notification post` để demo (plan mục 7). */
    val debugOnly: Boolean = false,
) {
    VIETCOMBANK("Vietcombank", setOf("com.VCB"), setOf("VIETCOMBANK", "VCB")),
    TECHCOMBANK("Techcombank", setOf("vn.com.techcombank.bb.app"), setOf("TECHCOMBANK")),
    MBBANK("MB Bank", setOf("com.mbmobile"), setOf("MBBANK", "MB BANK")),
    BIDV("BIDV", setOf("com.vnpay.bidv"), setOf("BIDV")),
    VIETINBANK("VietinBank", setOf("com.vietinbank.ipay"), setOf("VIETINBANK")),
    ACB("ACB", setOf("mobile.acb.com.vn"), setOf("ACB")),
    TPBANK("TPBank", setOf("com.tpb.mb.gprsandroid"), setOf("TPBANK")),
    DEMO("Ngân hàng demo", setOf("com.android.shell"), emptySet(), debugOnly = true),
    ;

    companion object {
        /** Package của app Tin nhắn hay gặp: thông báo SMS ngân hàng đi qua đây (plan L4). */
        val SMS_APP_PACKAGES: Set<String> = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.miui.mms",
        )

        fun fromName(name: String?): BankId? = entries.firstOrNull { it.name == name }
    }
}

enum class Direction { IN, OUT }

enum class Confidence { HIGH, LOW }

enum class IgnoreReason { OTP, NOT_VND, NOT_ACCOUNT_MOVEMENT }

/** Một tin cần đọc: thông báo của app ngân hàng, SMS, hoặc tin user dán vào. */
data class BankMessage(
    /** `null` khi chưa biết (màn dán): parser thử mọi mẫu. */
    val bank: BankId?,
    val title: String?,
    val text: String,
    /** `Notification.when`, hoặc lúc bấm "Đọc" ở màn dán. */
    val receivedAt: Instant,
) {
    /** Không in nội dung tin (B8). */
    override fun toString(): String = "BankMessage(bank=$bank)"
}

sealed interface ParseResult {
    data class Parsed(val event: ParsedBankEvent) : ParseResult
    data class Ignored(val reason: IgnoreReason) : ParseResult

    /** Nhận ra là tin ngân hàng nhưng không tách được chiều + số tiền. */
    data object Unparsed : ParseResult
}

data class ParsedBankEvent(
    val bank: BankId?,
    val direction: Direction,
    /** VND không phần lẻ. */
    val amountMinor: Long,
    /** Chỉ dùng tính fingerprint, **không** lưu xuống DB. */
    val balanceAfterMinor: Long?,
    /** Đúng 4 số cuối tài khoản. */
    val accountHint: String?,
    /** Đã che số (P7), tối đa [MAX_DESCRIPTION]. */
    val description: String?,
    val payee: String?,
    /** Giờ ghi trong nội dung tin. */
    val postedAt: LocalDateTime?,
    val confidence: Confidence,
) {
    /** Không in số tiền, số dư, nội dung (B8). */
    override fun toString(): String = "ParsedBankEvent(bank=$bank, direction=$direction, confidence=$confidence)"

    companion object {
        const val MAX_DESCRIPTION = 500
        const val MAX_PAYEE = 100
    }
}

/** Trạng thái một dòng `capture_event`. */
enum class CaptureStatus { DRAFTED, NEEDS_ACCOUNT, UNPARSED, DISMISSED }

/** Dòng nhật ký capture hiển thị ở màn Chờ xác nhận (mục "Cần chọn ví", "Không đọc được"). */
data class CaptureEvent(
    val id: String,
    val bank: BankId?,
    val status: CaptureStatus,
    val direction: Direction?,
    val amountMinor: Long?,
    val accountHint: String?,
    val payee: String?,
    val note: String?,
    val occurredOn: LocalDate?,
    val confidence: Confidence?,
    val rawText: String?,
    val receivedAt: Instant,
) {
    override fun toString(): String = "CaptureEvent(id=$id, bank=$bank, status=$status)"
}

/** Cài đặt capture của user đang đăng nhập. */
data class CaptureSettings(
    val enabled: Boolean,
    val banks: Set<BankId>,
    val defaultExpenseCategoryId: String?,
    val defaultIncomeCategoryId: String?,
) {
    companion object {
        val DEFAULT = CaptureSettings(enabled = false, banks = emptySet(), defaultExpenseCategoryId = null, defaultIncomeCategoryId = null)
    }
}

/** Kết quả một lần xử lý tin, cũng là nhãn log (S1): không mang số tiền hay nội dung. */
enum class IngestOutcome { OTP, IGNORED, UNPARSED, NO_SESSION, DISABLED, DRAFTED, SAVED, ALREADY_EXISTS, NEEDS_ACCOUNT, REJECTED }

/** Bản đang chạy có phải debug không (composeApp cung cấp): chỉ debug mới chọn được [BankId.DEMO]. */
data class CaptureEnvironment(val debug: Boolean)

/** Hằng số thông báo của capture mà composeApp cần biết. */
object CaptureNotifications {
    /** Bấm thông báo "Có N giao dịch cần xác nhận": mở màn Chờ xác nhận. */
    const val ACTION_OPEN_INBOX = "capture_inbox"
}
