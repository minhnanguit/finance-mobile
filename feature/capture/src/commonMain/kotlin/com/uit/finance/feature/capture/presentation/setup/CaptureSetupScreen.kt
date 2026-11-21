package com.uit.finance.feature.capture.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.NoticeBanner
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.SettingsChevron
import com.uit.finance.core.designsystem.component.SettingsExternalMark
import com.uit.finance.core.designsystem.component.SettingsGroup
import com.uit.finance.core.designsystem.component.SettingsItem
import com.uit.finance.core.designsystem.component.SettingsSwitch
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CaptureSetupRoute(
    onBack: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenPaste: () -> Unit,
    viewModel: CaptureSetupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Quay lại từ cài đặt máy: đọc lại quyền.
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(CaptureSetupIntent.ScreenResumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CaptureSetupEffect.OpenInbox -> onOpenInbox()
                CaptureSetupEffect.OpenPaste -> onOpenPaste()
            }
        }
    }
    CaptureSetupScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

/**
 * Cài đặt "Tự ghi từ thông báo ngân hàng" (plan mục 1): tắt mặc định; bật → cấp quyền đọc thông báo → chọn ngân hàng
 * → chọn danh mục mặc định. iOS không đọc được thông báo của app khác nên chỉ có hai lối vào.
 */
@Composable
internal fun CaptureSetupScreen(
    state: CaptureSetupState,
    onIntent: (CaptureSetupIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item(key = "top-bar") { FinanceTopBar(title = "Tự ghi từ thông báo", onBack = onBack) }

        if (!state.listenerSupported) {
            item(key = "ios-note") {
                NoticeBanner(
                    message = "iPhone không cho ứng dụng đọc thông báo của ứng dụng khác. Bạn có thể sao chép tin nhắn " +
                        "biến động số dư rồi dán vào đây để ghi nhanh.",
                    modifier = horizontal,
                )
            }
        } else {
            item(key = "enable") {
                SettingsGroup(title = null, modifier = horizontal) {
                    SettingsItem(
                        icon = FinanceIcons.Inbox,
                        title = "Tự ghi giao dịch nháp",
                        subtitle = "Đọc thông báo biến động số dư của ngân hàng bạn chọn. Nháp không tính vào số dư cho tới khi bạn xác nhận",
                        onClick = { onIntent(CaptureSetupIntent.EnabledToggled(!state.enabled)) },
                        trailing = {
                            SettingsSwitch(checked = state.enabled, onCheckedChange = { onIntent(CaptureSetupIntent.EnabledToggled(it)) })
                        },
                    )
                    SettingsItem(
                        icon = FinanceIcons.ShieldCheck,
                        title = "Quyền đọc thông báo",
                        subtitle = if (state.accessGranted) "Đã cấp" else "Chưa cấp. Bấm để mở cài đặt máy",
                        onClick = { onIntent(CaptureSetupIntent.OpenAccessSettingsClicked) },
                        trailing = { SettingsExternalMark() },
                    )
                }
            }
            if (state.enabled && !state.accessGranted) {
                item(key = "restricted-note") {
                    NoticeBanner(
                        message = "Nếu máy không cho bật quyền (ứng dụng cài từ file APK trên Android 13 trở lên): mở " +
                            "Cài đặt → Ứng dụng → Finance → ⋮ → Cho phép cài đặt bị hạn chế, rồi bật lại.",
                        warning = true,
                        modifier = horizontal,
                    )
                }
            }
            item(key = "privacy-note") {
                NoticeBanner(
                    message = "Chỉ đọc thông báo của ngân hàng bạn chọn. Tin OTP bị bỏ ngay, không lưu. Nội dung tin chỉ " +
                        "nằm trên máy này và tự xoá sau 14 ngày.",
                    modifier = horizontal,
                )
            }
            item(key = "banks") {
                SettingsGroup(title = "Ngân hàng", modifier = horizontal) {
                    state.availableBanks.forEach { bank ->
                        val selected = bank in state.selectedBanks
                        SettingsItem(
                            icon = FinanceIcons.Bank,
                            title = bank.displayName,
                            onClick = { onIntent(CaptureSetupIntent.BankToggled(bank, !selected)) },
                            trailing = {
                                SettingsSwitch(checked = selected, onCheckedChange = { onIntent(CaptureSetupIntent.BankToggled(bank, it)) })
                            },
                        )
                    }
                }
            }
            item(key = "categories") {
                SettingsGroup(title = "Danh mục mặc định", modifier = horizontal) {
                    SettingsItem(
                        icon = FinanceIcons.Expense,
                        title = "Khoản chi",
                        subtitle = state.expenseCategoryName,
                        onClick = { onIntent(CaptureSetupIntent.PickCategoryClicked(CategoryKind.EXPENSE)) },
                        trailing = { SettingsChevron() },
                    )
                    SettingsItem(
                        icon = FinanceIcons.Income,
                        title = "Khoản thu",
                        subtitle = state.incomeCategoryName,
                        onClick = { onIntent(CaptureSetupIntent.PickCategoryClicked(CategoryKind.INCOME)) },
                        trailing = { SettingsChevron() },
                    )
                }
            }
        }

        item(key = "entries") {
            SettingsGroup(title = null, modifier = horizontal) {
                SettingsItem(
                    icon = FinanceIcons.Check,
                    title = "Chờ xác nhận",
                    subtitle = if (state.pendingCount > 0) "${state.pendingCount} mục cần xem" else "Không có mục nào",
                    onClick = { onIntent(CaptureSetupIntent.InboxClicked) },
                    trailing = { SettingsChevron() },
                )
                SettingsItem(
                    icon = FinanceIcons.Note,
                    title = "Dán tin nhắn ngân hàng",
                    subtitle = "Sao chép tin biến động số dư rồi dán vào để ghi",
                    onClick = { onIntent(CaptureSetupIntent.PasteClicked) },
                    trailing = { SettingsChevron() },
                )
            }
        }
    }

    state.pickingCategory?.let { kind ->
        val categories = if (kind == CategoryKind.EXPENSE) state.expenseCategories else state.incomeCategories
        OptionSheet(
            title = if (kind == CategoryKind.EXPENSE) "Danh mục chi mặc định" else "Danh mục thu mặc định",
            options = categories.map { SheetOption(key = it.id, value = it.id, title = it.name) },
            selected = if (kind == CategoryKind.EXPENSE) state.defaultExpenseCategoryId else state.defaultIncomeCategoryId,
            onSelect = { onIntent(CaptureSetupIntent.CategoryPicked(it)) },
            onDismiss = { onIntent(CaptureSetupIntent.CategoryPickerDismissed) },
        )
    }
}
