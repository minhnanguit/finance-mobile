package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.asFailure
import com.uit.finance.core.common.result.map
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.ledger.domain.validation.LedgerRules
import com.uit.finance.feature.transactions.domain.model.FEE_TEMPLATE_KEY
import com.uit.finance.feature.transactions.domain.model.TransactionErrorCode
import com.uit.finance.feature.transactions.domain.model.TransactionInput
import kotlinx.coroutines.flow.first

/**
 * Lưu giao dịch từ form.
 *
 * - Thêm mới: một dòng. Chuyển tiền có phí (D3): thêm một khoản chi danh mục "Phí giao dịch" từ ví đi, cùng ngày;
 *   hai dòng ghi trong **một** transaction nên không bao giờ có dòng chuyển mà mất dòng phí.
 * - Sửa: chỉ sửa dòng đó; giữ trạng thái và giờ gốc. Khoản phí là giao dịch riêng, sửa riêng.
 * - Giờ (D5): ghi cho hôm nay thì lấy giờ hiện tại để danh sách xếp đúng thứ tự trong ngày; ghi bù ngày khác thì
 *   không rõ giờ nên để trống.
 */
class SaveTransactionUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: TransactionInput): AppResult<Unit> {
        if (input.id != null) return update(input)
        val occurredAt = clock.now().takeIf { input.occurredOn == clock.today() }
        val main = input.toDraft().copy(occurredAt = occurredAt)
        if (input.type != TransactionType.TRANSFER || input.feeMinor <= 0) {
            return transactions.record(main).map { }
        }
        if (LedgerRules.amount(input.feeMinor).isNotEmpty()) {
            return AppError.Validation("feeMinor", LedgerErrorCode.INVALID_FIELD).asFailure()
        }
        val feeCategory = categories.observeCategories().first().firstOrNull { it.templateKey == FEE_TEMPLATE_KEY && !it.archived }
            ?: return AppError.Validation("feeMinor", TransactionErrorCode.FEE_CATEGORY_MISSING).asFailure()
        val fee = TransactionDraft(
            type = TransactionType.EXPENSE,
            accountId = input.accountId,
            amountMinor = input.feeMinor,
            occurredOn = input.occurredOn,
            categoryId = feeCategory.id,
            occurredAt = occurredAt,
            note = FEE_NOTE,
        )
        return transactions.recordAll(listOf(main, fee)).map { }
    }

    private suspend fun update(input: TransactionInput): AppResult<Unit> {
        val existing = transactions.observe(requireNotNull(input.id)).first()
            ?: return AppError.Validation("transaction", LedgerErrorCode.NOT_FOUND).asFailure()
        val isTransfer = input.type == TransactionType.TRANSFER
        val updated = existing.copy(
            type = input.type,
            accountId = input.accountId,
            counterAccountId = if (isTransfer) input.counterAccountId else null,
            categoryId = if (isTransfer) null else input.categoryId,
            // Tiền tệ do repository lấy lại theo ví khi lưu.
            amount = Money(input.amountMinor, existing.amount.currency),
            occurredOn = input.occurredOn,
            // Đổi sang ngày khác thì giờ cũ không còn đúng.
            occurredAt = existing.occurredAt.takeIf { input.occurredOn == existing.occurredOn },
            payee = input.payee,
            note = input.note,
        )
        return transactions.update(updated).map { }
    }

    private fun TransactionInput.toDraft(): TransactionDraft {
        val isTransfer = type == TransactionType.TRANSFER
        return TransactionDraft(
            type = type,
            accountId = accountId,
            amountMinor = amountMinor,
            occurredOn = occurredOn,
            categoryId = if (isTransfer) null else categoryId,
            counterAccountId = if (isTransfer) counterAccountId else null,
            payee = payee,
            note = note,
        )
    }

    private companion object {
        const val FEE_NOTE = "Phí chuyển tiền"
    }
}
