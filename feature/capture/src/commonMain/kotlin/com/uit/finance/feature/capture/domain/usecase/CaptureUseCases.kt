package com.uit.finance.feature.capture.domain.usecase

import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.CaptureSettings
import com.uit.finance.core.capture.domain.model.IngestOutcome
import com.uit.finance.core.capture.domain.repository.CaptureInbox
import com.uit.finance.core.capture.domain.repository.CaptureRepository
import com.uit.finance.core.capture.domain.repository.CaptureSettingsRepository
import com.uit.finance.core.capture.domain.repository.PasteParseResult
import com.uit.finance.core.capture.domain.repository.PasteSubmission
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.notification.NotificationAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Dữ liệu cho màn cài đặt: cài đặt hiện tại + ngân hàng chọn được + danh mục để chọn mặc định. */
data class CaptureSetupData(
    val settings: CaptureSettings,
    val availableBanks: List<BankId>,
    val expenseCategories: List<Category>,
    val incomeCategories: List<Category>,
    val pendingCount: Int,
)

class ObserveCaptureSetupUseCase(
    private val settings: CaptureSettingsRepository,
    private val categories: CategoryRepository,
    private val capture: CaptureRepository,
) {
    operator fun invoke(): Flow<CaptureSetupData> =
        combine(settings.observe(), categories.observeCategories(), capture.observePendingCount()) { current, all, pending ->
            val usable = all.filter { !it.archived }
            CaptureSetupData(
                settings = current,
                availableBanks = settings.availableBanks(),
                expenseCategories = usable.filter { it.kind == CategoryKind.EXPENSE },
                incomeCategories = usable.filter { it.kind == CategoryKind.INCOME },
                pendingCount = pending,
            )
        }
}

class SetCaptureEnabledUseCase(private val settings: CaptureSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = settings.setEnabled(enabled)
}

class SetCaptureBanksUseCase(private val settings: CaptureSettingsRepository) {
    suspend operator fun invoke(banks: Set<BankId>) = settings.setBanks(banks)
}

class SetDefaultCategoryUseCase(private val settings: CaptureSettingsRepository) {
    suspend operator fun invoke(kind: CategoryKind, categoryId: String?) = when (kind) {
        CategoryKind.EXPENSE -> settings.setDefaultExpenseCategory(categoryId)
        CategoryKind.INCOME -> settings.setDefaultIncomeCategory(categoryId)
    }
}

/** Quyền đọc thông báo của app khác: `null` khi nền tảng không hỗ trợ (iOS). */
class GetListenerAccessUseCase(private val access: NotificationAccess) {
    operator fun invoke(): Boolean? = if (access.isSupported) access.isGranted() else null
}

class OpenListenerSettingsUseCase(private val access: NotificationAccess) {
    operator fun invoke() = access.openSettings()
}

/** Màn Chờ xác nhận: hộp thư + ví/danh mục để hiện tên. */
data class CaptureInboxData(
    val inbox: CaptureInbox,
    val accounts: List<Account>,
    val categories: List<Category>,
)

class ObserveCaptureInboxUseCase(
    private val capture: CaptureRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(): Flow<CaptureInboxData> =
        combine(capture.observeInbox(), accounts.observeAccounts(), categories.observeCategories(), ::CaptureInboxData)
}

class ConfirmDraftUseCase(private val capture: CaptureRepository) {
    suspend operator fun invoke(transactionId: String): AppResult<Unit> = capture.confirm(transactionId)
}

class DismissDraftUseCase(private val capture: CaptureRepository) {
    suspend operator fun invoke(transactionId: String): AppResult<Unit> = capture.dismissDraft(transactionId)
}

class DismissCaptureEventUseCase(private val capture: CaptureRepository) {
    suspend operator fun invoke(eventId: String): AppResult<Unit> = capture.dismissEvent(eventId)
}

class LinkCaptureAccountUseCase(private val capture: CaptureRepository) {
    suspend operator fun invoke(bank: BankId, accountHint: String?, accountId: String): AppResult<Int> =
        capture.linkAccount(bank, accountHint, accountId)
}

/** Mở màn Chờ xác nhận: dọn hạn và gỡ thông báo đang hiện. */
class OpenCaptureInboxUseCase(private val capture: CaptureRepository) {
    suspend operator fun invoke() = capture.openInbox()
}

/** Ví VND còn dùng được, danh mục còn dùng được, ngân hàng chọn được: cho form dán tin. */
data class PasteFormData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val banks: List<BankId>,
)

class ObservePasteFormDataUseCase(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val settings: CaptureSettingsRepository,
) {
    operator fun invoke(): Flow<PasteFormData> = combine(accounts.observeAccounts(), categories.observeCategories()) { a, c ->
        PasteFormData(
            accounts = a.filter { !it.archived && it.currency == "VND" },
            categories = c.filter { !it.archived },
            banks = settings.availableBanks(),
        )
    }
}

class ParsePastedMessageUseCase(private val capture: CaptureRepository) {
    suspend operator fun invoke(text: String, bank: BankId?): PasteParseResult = capture.parsePasted(text, bank)
}

class SavePastedMessageUseCase(private val capture: CaptureRepository) {
    suspend operator fun invoke(submission: PasteSubmission): AppResult<IngestOutcome> = capture.savePasted(submission)
}
