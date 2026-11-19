@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.uit.finance.feature.capture

import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.BankMessage
import com.uit.finance.core.capture.domain.model.CaptureSettings
import com.uit.finance.core.capture.domain.model.IngestOutcome
import com.uit.finance.core.capture.domain.model.ParseResult
import com.uit.finance.core.capture.domain.parser.BankMessageParser
import com.uit.finance.core.capture.domain.repository.CaptureInbox
import com.uit.finance.core.capture.domain.repository.CaptureRepository
import com.uit.finance.core.capture.domain.repository.CaptureSettingsRepository
import com.uit.finance.core.capture.domain.repository.PasteParseResult
import com.uit.finance.core.capture.domain.repository.PastePreview
import com.uit.finance.core.capture.domain.repository.PasteSubmission
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.notification.NotificationAccess
import com.uit.finance.feature.capture.domain.usecase.GetListenerAccessUseCase
import com.uit.finance.feature.capture.domain.usecase.ObserveCaptureSetupUseCase
import com.uit.finance.feature.capture.domain.usecase.ObservePasteFormDataUseCase
import com.uit.finance.feature.capture.domain.usecase.OpenListenerSettingsUseCase
import com.uit.finance.feature.capture.domain.usecase.ParsePastedMessageUseCase
import com.uit.finance.feature.capture.domain.usecase.SavePastedMessageUseCase
import com.uit.finance.feature.capture.domain.usecase.SetCaptureBanksUseCase
import com.uit.finance.feature.capture.domain.usecase.SetCaptureEnabledUseCase
import com.uit.finance.feature.capture.domain.usecase.SetDefaultCategoryUseCase
import com.uit.finance.feature.capture.presentation.paste.PasteMessageEffect
import com.uit.finance.feature.capture.presentation.paste.PasteMessageIntent
import com.uit.finance.feature.capture.presentation.paste.PasteMessageViewModel
import com.uit.finance.feature.capture.presentation.setup.CaptureSetupIntent
import com.uit.finance.feature.capture.presentation.setup.CaptureSetupViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class CaptureViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeCaptureSettings()
    private val capture = FakeCaptureRepository()
    private val access = FakeAccess()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        accounts.accounts.value = listOf(Account("wallet", "Ví VCB", AccountType.BANK, Money(0, "VND"), 0, false))
        categories.categories.value = listOf(
            Category("food", CategoryKind.EXPENSE, "Ăn uống", null, null, null, null, false),
            Category("salary", CategoryKind.INCOME, "Lương", null, null, null, null, false),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun setupViewModel() = CaptureSetupViewModel(
        ObserveCaptureSetupUseCase(settings, categories, capture),
        GetListenerAccessUseCase(access),
        OpenListenerSettingsUseCase(access),
        SetCaptureEnabledUseCase(settings),
        SetCaptureBanksUseCase(settings),
        SetDefaultCategoryUseCase(settings),
    )

    private fun pasteViewModel() = PasteMessageViewModel(
        ObservePasteFormDataUseCase(accounts, categories, settings),
        ParsePastedMessageUseCase(capture),
        SavePastedMessageUseCase(capture),
    )

    @Test
    fun `bat tinh nang khi chua co quyen thi mo cai dat may`() = runTest(dispatcher) {
        val viewModel = setupViewModel()
        advanceUntilIdle()

        viewModel.onIntent(CaptureSetupIntent.EnabledToggled(true))
        advanceUntilIdle()

        assertTrue(settings.current.value.enabled)
        assertEquals(1, access.opened)
        assertFalse(viewModel.state.value.isActive)
    }

    @Test
    fun `quay lai sau khi cap quyen thi doc lai quyen`() = runTest(dispatcher) {
        val viewModel = setupViewModel()
        advanceUntilIdle()
        access.granted = true

        viewModel.onIntent(CaptureSetupIntent.ScreenResumed)
        viewModel.onIntent(CaptureSetupIntent.EnabledToggled(true))
        viewModel.onIntent(CaptureSetupIntent.BankToggled(BankId.VIETCOMBANK, true))
        advanceUntilIdle()

        assertEquals(0, access.opened)
        assertTrue(viewModel.state.value.isActive)
    }

    @Test
    fun `dan tin doc ra form dien san`() = runTest(dispatcher) {
        val viewModel = pasteViewModel()
        viewModel.onIntent(PasteMessageIntent.TextChanged(SMS))
        viewModel.onIntent(PasteMessageIntent.ReadClicked)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.preview)
        assertEquals("45000", state.amountInput)
        assertEquals("food", state.categoryId)
        assertEquals("HIGHLANDS COFFEE", state.note)
        assertFalse(state.isIncome)
    }

    @Test
    fun `dan lai dung tin da ghi thi bao da ghi`() = runTest(dispatcher) {
        capture.saveOutcome = IngestOutcome.ALREADY_EXISTS
        val viewModel = pasteViewModel()
        viewModel.onIntent(PasteMessageIntent.TextChanged(SMS))
        viewModel.onIntent(PasteMessageIntent.ReadClicked)
        advanceUntilIdle()
        viewModel.onIntent(PasteMessageIntent.AccountPicked("wallet"))
        viewModel.onIntent(PasteMessageIntent.SaveClicked)
        advanceUntilIdle()

        assertEquals("Giao dịch này đã được ghi rồi.", viewModel.state.value.error?.resolve())
    }

    @Test
    fun `luu thanh cong thi dong man`() = runTest(dispatcher) {
        val viewModel = pasteViewModel()
        viewModel.onIntent(PasteMessageIntent.TextChanged(SMS))
        viewModel.onIntent(PasteMessageIntent.ReadClicked)
        advanceUntilIdle()
        viewModel.onIntent(PasteMessageIntent.AccountPicked("wallet"))
        viewModel.onIntent(PasteMessageIntent.SaveClicked)
        advanceUntilIdle()

        assertEquals(PasteMessageEffect.Saved, viewModel.effects.first())
        assertEquals(45_000, capture.saved.single().amountMinor)
    }

    @Test
    fun `tin OTP khong hien form`() = runTest(dispatcher) {
        val viewModel = pasteViewModel()
        viewModel.onIntent(PasteMessageIntent.TextChanged("Ma OTP cua ban la 123456"))
        viewModel.onIntent(PasteMessageIntent.ReadClicked)
        advanceUntilIdle()

        assertNull(viewModel.state.value.preview)
        assertNotNull(viewModel.state.value.error)
    }

    private companion object {
        const val SMS = "TK ...5678 -45,000VND luc 09:15 16/11/2026. SD: 1,189,567VND. ND: HIGHLANDS COFFEE"
    }
}

private class FakeCaptureSettings : CaptureSettingsRepository {
    val current = MutableStateFlow(CaptureSettings.DEFAULT.copy(defaultExpenseCategoryId = "food"))
    override fun observe(): Flow<CaptureSettings> = current
    override suspend fun setEnabled(enabled: Boolean) { current.value = current.value.copy(enabled = enabled) }
    override suspend fun setBanks(banks: Set<BankId>) { current.value = current.value.copy(banks = banks) }
    override suspend fun setDefaultExpenseCategory(categoryId: String?) { current.value = current.value.copy(defaultExpenseCategoryId = categoryId) }
    override suspend fun setDefaultIncomeCategory(categoryId: String?) { current.value = current.value.copy(defaultIncomeCategoryId = categoryId) }
    override fun availableBanks(): List<BankId> = BankId.entries.filter { !it.debugOnly }
}

private class FakeAccess : NotificationAccess {
    var granted = false
    var opened = 0
    override val isSupported: Boolean = true
    override fun isGranted(): Boolean = granted
    override fun openSettings() { opened++ }
    override fun requestRebind() = Unit
}

private class FakeCaptureRepository : CaptureRepository {
    var saveOutcome = IngestOutcome.SAVED
    val saved = mutableListOf<PasteSubmission>()
    override fun observeInbox(): Flow<CaptureInbox> = flowOf(CaptureInbox.EMPTY)
    override fun observePendingCount(): Flow<Int> = flowOf(0)
    override suspend fun confirm(transactionId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun dismissDraft(transactionId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun dismissEvent(eventId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun linkAccount(bank: BankId, accountHint: String?, accountId: String): AppResult<Int> = AppResult.Success(0)
    override suspend fun purgeExpired() = Unit

    /** Dùng parser thật để form điền sẵn đúng như app. */
    override suspend fun parsePasted(text: String, bank: BankId?): PasteParseResult =
        when (val result = BankMessageParser.parse(BankMessage(bank, null, text, Instant.parse("2026-11-16T02:20:00Z")))) {
            is ParseResult.Parsed -> PasteParseResult.Ready(PastePreview(result.event, LocalDate(2026, 11, 16), null, "food"))
            is ParseResult.Ignored -> PasteParseResult.Otp
            ParseResult.Unparsed -> PasteParseResult.NotReadable
        }

    override suspend fun savePasted(submission: PasteSubmission): AppResult<IngestOutcome> {
        saved += submission
        return AppResult.Success(saveOutcome)
    }
}
