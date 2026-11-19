package com.uit.finance.feature.capture.presentation.setup

import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind

data class CaptureSetupState(
    val isLoading: Boolean = true,
    /** `false` trên iOS: chỉ có màn dán tin và Chờ xác nhận. */
    val listenerSupported: Boolean = true,
    val accessGranted: Boolean = false,
    val enabled: Boolean = false,
    val availableBanks: List<BankId> = emptyList(),
    val selectedBanks: Set<BankId> = emptySet(),
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val defaultExpenseCategoryId: String? = null,
    val defaultIncomeCategoryId: String? = null,
    val pendingCount: Int = 0,
    /** Đang mở bảng chọn danh mục mặc định cho loại nào. */
    val pickingCategory: CategoryKind? = null,
) {
    val expenseCategoryName: String get() = expenseCategories.firstOrNull { it.id == defaultExpenseCategoryId }?.name ?: "Tự chọn danh mục đầu tiên"
    val incomeCategoryName: String get() = incomeCategories.firstOrNull { it.id == defaultIncomeCategoryId }?.name ?: "Tự chọn danh mục đầu tiên"
    val isActive: Boolean get() = enabled && accessGranted && selectedBanks.isNotEmpty()
}

sealed interface CaptureSetupIntent {
    data object ScreenResumed : CaptureSetupIntent
    data class EnabledToggled(val enabled: Boolean) : CaptureSetupIntent
    data object OpenAccessSettingsClicked : CaptureSetupIntent
    data class BankToggled(val bank: BankId, val selected: Boolean) : CaptureSetupIntent
    data class PickCategoryClicked(val kind: CategoryKind) : CaptureSetupIntent
    data class CategoryPicked(val categoryId: String) : CaptureSetupIntent
    data object CategoryPickerDismissed : CaptureSetupIntent
    data object InboxClicked : CaptureSetupIntent
    data object PasteClicked : CaptureSetupIntent
}

sealed interface CaptureSetupEffect {
    data object OpenInbox : CaptureSetupEffect
    data object OpenPaste : CaptureSetupEffect
}
