package com.uit.finance.feature.settings.presentation.deleteaccount

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.settings.domain.usecase.DeleteAccountUseCase
import com.uit.finance.feature.settings.domain.usecase.ObserveAccountProfileUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Xoá tài khoản (ADR-008 A1–A3): giải thích → gõ XOÁ → đăng nhập lại trong trình duyệt → xoá. Chỉ khi server đã
 * nhận yêu cầu mới báo [DeleteAccountEffect.Deleted]; mọi kết quả khác giữ user ở màn này với lời giải thích.
 */
internal class DeleteAccountViewModel(
    observeProfile: ObserveAccountProfileUseCase,
    private val countUnsentChanges: CountUnsentChangesUseCase,
    private val deleteAccount: DeleteAccountUseCase,
) : MviViewModel<DeleteAccountState, DeleteAccountIntent, DeleteAccountEffect>(DeleteAccountState()) {

    init {
        observeProfile()
            .onEach { profile -> setState { copy(email = profile.email ?: email) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val unsent = countUnsentChanges()
            setState { copy(unsentChanges = unsent) }
        }
    }

    override fun onIntent(intent: DeleteAccountIntent) {
        when (intent) {
            is DeleteAccountIntent.ConfirmationChanged -> setState { copy(confirmation = intent.value, error = null) }
            DeleteAccountIntent.Submit -> submit()
            DeleteAccountIntent.BackClicked -> if (!currentState.isDeleting) sendEffect(DeleteAccountEffect.NavigateBack)
            DeleteAccountIntent.ErrorDismissed -> setState { copy(error = null) }
        }
    }

    private fun submit() {
        if (!currentState.canSubmit) return
        setState { copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            deleteAccount(currentState.confirmation).fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        AccountDeletion.Deleted -> sendEffect(DeleteAccountEffect.Deleted)
                        AccountDeletion.Cancelled -> setState { copy(isDeleting = false) }
                        AccountDeletion.DifferentAccount -> setState { copy(isDeleting = false, error = WRONG_ACCOUNT) }
                        AccountDeletion.ConfirmationExpired -> setState { copy(isDeleting = false, error = EXPIRED) }
                        AccountDeletion.Unconfirmed -> setState { copy(isDeleting = false, error = UNCONFIRMED) }
                    }
                },
                onFailure = { error -> setState { copy(isDeleting = false).withError(error) } },
            )
        }
    }

    private companion object {
        val WRONG_ACCOUNT = UiText.Key(
            "settings.delete.wrong_account",
            "Bạn vừa đăng nhập bằng một tài khoản khác. Hãy xác nhận bằng đúng tài khoản cần xoá.",
        )
        val EXPIRED = UiText.Key(
            "settings.delete.expired",
            "Bước xác nhận đã quá hạn. Hãy bấm xoá lần nữa và đăng nhập lại.",
        )

        // Không nói "đã xoá" hay "chưa xoá" khi không chắc. Đã xoá thì lần gọi kế tiếp nhận user_deleted và app tự dọn.
        val UNCONFIRMED = UiText.Key(
            "settings.delete.unconfirmed",
            "Mất kết nối giữa chừng nên chưa rõ tài khoản đã được xoá chưa. Có mạng lại thì thử lại; nếu đã xoá, " +
                "app sẽ tự đăng xuất.",
        )
    }
}

internal fun DeleteAccountState.withError(error: AppError): DeleteAccountState = when (error) {
    is AppError.Network -> copy(
        error = UiText.Key("settings.delete.offline", "Cần kết nối mạng để xoá tài khoản. Dữ liệu chưa bị xoá."),
    )
    // Phiên hết hạn hoặc vừa đổi trong lúc xác nhận: không xoá gì.
    AppError.Unauthorized -> copy(
        error = UiText.Key("settings.delete.session", "Phiên đăng nhập đã thay đổi. Dữ liệu chưa bị xoá, hãy thử lại."),
    )
    // Tới đây là trường hợp chắc chắn chưa xoá (lỗi trước khi gửi, 4xx, hoặc đã hỏi lại server); không chắc thì
    // repository trả AccountDeletion.Unconfirmed. Không đưa title kỹ thuật của server ra màn hình.
    is AppError.Api -> copy(
        error = if (error.status == TOO_MANY_REQUESTS) {
            UiText.Key("settings.delete.rate_limited", "Bạn đã thử nhiều lần. Đợi một phút rồi thử lại. Dữ liệu chưa bị xoá.")
        } else {
            UiText.Key("settings.delete.server", "Máy chủ chưa xoá được tài khoản. Dữ liệu chưa bị xoá, hãy thử lại sau.")
        },
    )
    else -> copy(error = error.toUiText())
}

private const val TOO_MANY_REQUESTS = 429
