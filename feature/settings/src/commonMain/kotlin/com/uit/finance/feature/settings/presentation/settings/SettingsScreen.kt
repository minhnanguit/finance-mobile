package com.uit.finance.feature.settings.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.Avatar
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.SecondaryButton
import com.uit.finance.core.designsystem.component.SettingsChevron
import com.uit.finance.core.designsystem.component.SettingsExternalMark
import com.uit.finance.core.designsystem.component.SettingsGroup
import com.uit.finance.core.designsystem.component.SettingsItem
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsRoute(
    onOpenDeleteAccount: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.OpenUrl -> uriHandler.openUri(effect.url)
                SettingsEffect.OpenDeleteAccount -> onOpenDeleteAccount()
                SettingsEffect.LoggedOut -> onLoggedOut()
            }
        }
    }
    SettingsScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * Tab "Cá nhân" (ADR-008 C1): chỉ những gì user cần làm với tài khoản — hồ sơ, đổi mật khẩu, chính sách quyền riêng
 * tư + phiên bản, rồi xoá tài khoản và đăng xuất ở cuối (việc càng nặng càng nằm xa ngón tay). Đồng bộ không có ở đây:
 * app tự đồng bộ, trạng thái và "làm mới" nằm ở Trang chủ.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "top-bar") { FinanceTopBar(title = "Cá nhân") }
            item(key = "profile") { ProfileCard(state.profile, horizontal) }
            item(key = "account") {
                SettingsGroup(title = "Tài khoản", modifier = horizontal) {
                    SettingsItem(
                        icon = FinanceIcons.Key,
                        title = "Đổi mật khẩu",
                        enabled = !state.isChangingPassword,
                        onClick = { onIntent(SettingsIntent.ChangePasswordClicked) },
                        trailing = {
                            if (state.isChangingPassword) InlineProgress() else SettingsExternalMark()
                        },
                    )
                }
            }
            item(key = "about") {
                SettingsGroup(title = "Về ứng dụng", modifier = horizontal) {
                    SettingsItem(
                        icon = FinanceIcons.ShieldCheck,
                        title = "Chính sách quyền riêng tư",
                        onClick = { onIntent(SettingsIntent.PrivacyPolicyClicked) },
                        trailing = { SettingsExternalMark() },
                    )
                    SettingsItem(
                        icon = FinanceIcons.Info,
                        title = "Phiên bản",
                        trailing = {
                            Text(
                                state.versionName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
            item(key = "danger") {
                SettingsGroup(title = null, modifier = horizontal) {
                    SettingsItem(
                        icon = FinanceIcons.DeleteAccount,
                        title = "Xoá tài khoản",
                        subtitle = "Xoá vĩnh viễn sổ thu chi và tài khoản đăng nhập",
                        destructive = true,
                        onClick = { onIntent(SettingsIntent.DeleteAccountClicked) },
                        trailing = { SettingsChevron() },
                    )
                }
            }
            item(key = "logout") {
                SecondaryButton(
                    text = "Đăng xuất",
                    icon = FinanceIcons.LogOut,
                    loading = state.isLoggingOut,
                    onClick = { onIntent(SettingsIntent.LogoutClicked) },
                    modifier = horizontal,
                )
            }
        }
        MessageSnackbarHost(
            message = state.message?.resolve(),
            onShown = { onIntent(SettingsIntent.MessageShown) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(spacing.md),
        )
    }

    state.unsentChangesWarning?.let { unsent ->
        ConfirmDialog(
            title = "Còn thay đổi chưa đồng bộ",
            message = "Có $unsent thay đổi chưa lên máy chủ. Đăng xuất bây giờ sẽ xoá sổ trên máy này và mất các thay đổi đó.",
            confirmLabel = "Vẫn đăng xuất",
            dismissLabel = "Ở lại",
            destructive = true,
            onConfirm = { onIntent(SettingsIntent.LogoutConfirmed) },
            onDismiss = { onIntent(SettingsIntent.LogoutWarningDismissed) },
        )
    }
}

@Composable
private fun ProfileCard(profile: ProfileUi?, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.primaryContainer)
            .padding(spacing.lg)
            // Đọc một lần: tên, email, ngày tham gia — không đọc chữ viết tắt trong avatar.
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = profile?.displayName, modifier = Modifier.clearAndSetSemantics {})
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Tài khoản của bạn",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile?.email?.let { email ->
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            profile?.memberSince?.let { since ->
                Text(
                    text = since,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer.copy(alpha = SECONDARY_TEXT_ALPHA),
                )
            }
        }
    }
}

/** Vòng xoay nhỏ ở cuối dòng trong lúc đang chờ trình duyệt. */
@Composable
private fun InlineProgress() {
    CircularProgressIndicator(
        modifier = Modifier.padding(end = FinanceTheme.spacing.xs).size(FinanceTheme.spacing.lg),
        strokeWidth = FinanceTheme.spacing.xxs,
    )
}

private const val SECONDARY_TEXT_ALPHA = 0.72f
