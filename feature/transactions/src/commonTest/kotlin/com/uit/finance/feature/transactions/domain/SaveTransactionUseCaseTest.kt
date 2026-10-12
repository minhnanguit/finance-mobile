package com.uit.finance.feature.transactions.domain

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.ledger.testing.transaction
import com.uit.finance.core.testing.TestClock
import com.uit.finance.feature.transactions.domain.model.FEE_TEMPLATE_KEY
import com.uit.finance.feature.transactions.domain.model.TransactionErrorCode
import com.uit.finance.feature.transactions.domain.model.TransactionInput
import com.uit.finance.feature.transactions.domain.usecase.SaveTransactionUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class SaveTransactionUseCaseTest {

    private val transactions = FakeTransactionRepository()
    private val categories = FakeCategoryRepository()
    private val save = SaveTransactionUseCase(transactions, categories, TestClock(NOON_2023_11_14))
    private val day = LocalDate(2023, 11, 14)

    private fun input(
        type: TransactionType,
        amount: Long = 50_000,
        fee: Long = 0,
        categoryId: String? = "food",
        counterAccountId: String? = null,
        id: String? = null,
    ) = TransactionInput(id, type, "a", counterAccountId, categoryId, amount, fee, day, payee = null, note = null)

    @Test
    fun `khoản chi ghi đúng một dòng`() = runTest {
        save(input(TransactionType.EXPENSE))

        val batch = transactions.recordedBatches.single()
        assertEquals(1, batch.size)
        assertEquals("food", batch.single().categoryId)
        assertEquals(NOON_2023_11_14, batch.single().occurredAt) // ghi cho hôm nay: kèm giờ hiện tại
    }

    @Test
    fun `ghi bù ngày khác thì không có giờ`() = runTest {
        save(input(TransactionType.EXPENSE).copy(occurredOn = LocalDate(2023, 11, 2)))

        assertNull(transactions.recordedBatches.single().single().occurredAt)
    }

    @Test
    fun `chuyển tiền kèm phí ghi hai dòng cùng một lần`() = runTest {
        categories.categories.value = listOf(category("fee-id", "Phí giao dịch", templateKey = FEE_TEMPLATE_KEY))

        save(input(TransactionType.TRANSFER, amount = 1_000_000, fee = 3_300, categoryId = "ignored", counterAccountId = "b"))

        val (transfer, fee) = transactions.recordedBatches.single()
        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals("b", transfer.counterAccountId)
        assertNull(transfer.categoryId)
        assertEquals(TransactionType.EXPENSE, fee.type)
        assertEquals(3_300, fee.amountMinor)
        assertEquals("fee-id", fee.categoryId)
        assertEquals("a", fee.accountId)
    }

    @Test
    fun `chưa có danh mục phí thì báo lỗi ở ô phí và không ghi gì`() = runTest {
        val result = save(input(TransactionType.TRANSFER, fee = 3_300, counterAccountId = "b"))

        val error = (result as AppResult.Failure).error
        assertIs<AppError.Validation>(error)
        assertEquals(listOf(FieldError("feeMinor", TransactionErrorCode.FEE_CATEGORY_MISSING)), error.fieldErrors)
        assertEquals(emptyList(), transactions.recordedBatches)
    }

    @Test
    fun `sửa giữ trạng thái và giờ gốc - đổi sang chuyển tiền thì bỏ danh mục`() = runTest {
        transactions.transactions.value = listOf(transaction("t1", TransactionType.EXPENSE, "a", 10_000, day, categoryId = "food"))

        save(input(TransactionType.TRANSFER, amount = 20_000, counterAccountId = "b", id = "t1"))

        val updated = transactions.transactions.value.single()
        assertEquals(TransactionType.TRANSFER, updated.type)
        assertEquals(Money(20_000, "VND"), updated.amount)
        assertNull(updated.categoryId)
        assertEquals("b", updated.counterAccountId)
    }
}
