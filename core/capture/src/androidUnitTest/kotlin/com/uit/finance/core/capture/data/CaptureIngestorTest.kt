@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.uit.finance.core.capture.data.repository.CaptureRepositoryImpl
import com.uit.finance.core.capture.data.settings.CaptureDeviceKeys
import com.uit.finance.core.capture.data.settings.CaptureSettingsRepositoryImpl
import com.uit.finance.core.capture.data.settings.CaptureUserDataCleaner
import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.CaptureEnvironment
import com.uit.finance.core.capture.domain.model.CaptureStatus
import com.uit.finance.core.capture.domain.model.IngestOutcome
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.ledger.data.occurrence.ledgerOccurrenceWriter
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.CategoryPayload
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.notification.LocalNotification
import com.uit.finance.core.notification.LocalNotificationScheduler
import com.uit.finance.core.notification.NotificationIngress
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.SessionNotice
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.scheduler.SyncScheduler
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** Luồng xử lý tin trên schema thật (plan mục 5, dòng Ingest): chống trùng, ví, OTP, phiên, dọn hạn. */
class CaptureIngestorTest {

    private val clock = TestClock(Instant.parse("2026-11-16T02:20:00Z"))
    private val sms = "TK ...5678 -45,000VND luc 09:15 16/11/2026. SD: 1,189,567VND. ND: HIGHLANDS COFFEE"

    /** Một "máy": sổ riêng, cùng user. */
    private inner class Device(userId: String = USER) {
        val database = FinanceDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { FinanceDatabase.Schema.create(it) })
        val databases = object : UserDatabaseProvider {
            override val database = MutableStateFlow<FinanceDatabase?>(this@Device.database)
        }
        val session = FakeSession(LocalDataState.Ready(userId))
        val settingsStore = MemorySettings()
        val deviceKeys = CaptureDeviceKeys(settingsStore, CaptureEnvironment(debug = true))
        val settings = CaptureSettingsRepositoryImpl(settingsStore, session, deviceKeys)
        val accounts = FakeAccountRepository()
        val categories = FakeCategoryRepository()
        val notifications = RecordingScheduler()
        val notifier = CaptureInboxNotifier(notifications, { flowOf(1) }, clock)

        fun TestScope.ingestor() = CaptureIngestor(
            databases = databases,
            session = session,
            settings = settings,
            writer = ledgerOccurrenceWriter(OutboxWriter(FakeUuidGenerator(), clock), NoopLedgerCodec),
            accounts = accounts,
            categories = categories,
            syncScheduler = NoopScheduler,
            notifier = notifier,
            clock = clock,
            dispatchers = testDispatcherProvider(),
            logger = Logger.withTag("test"),
        )

        fun TestScope.repository(ingestor: CaptureIngestor) = CaptureRepositoryImpl(
            databases = databases,
            session = session,
            settings = settings,
            ingestor = ingestor,
            writer = ledgerOccurrenceWriter(OutboxWriter(FakeUuidGenerator(), clock), NoopLedgerCodec),
            transactions = FakeTransactionRepository(),
            notifier = notifier,
            syncScheduler = NoopScheduler,
            clock = clock,
            dispatchers = testDispatcherProvider(),
        )

        suspend fun setUp(link: Boolean = true, archived: Boolean = false) {
            database.accountQueries.upsert("wallet", "Ví", "BANK", "VND", 0, 0, if (archived) 1 else 0, 0)
            database.categoryQueries.upsert("food", "EXPENSE", "Ăn uống", null, null, null, null, 0, 0)
            accounts.accounts.value = listOf(Account("wallet", "Ví", AccountType.BANK, Money(0, "VND"), 0, archived))
            categories.categories.value = listOf(Category("food", CategoryKind.EXPENSE, "Ăn uống", null, null, null, null, false))
            settings.setEnabled(true)
            settings.setBanks(setOf(BankId.VIETCOMBANK))
            settings.setDefaultExpenseCategory("food")
            if (link) database.captureQueries.upsertLink(BankId.VIETCOMBANK.name, "5678", "wallet")
        }

        fun transactions() = database.ledgerTransactionQueries.selectRecent(100).executeAsList()
        fun events() = database.captureQueries.selectAll().executeAsList()
    }

    private suspend fun CaptureIngestor.post(text: String, pkg: String = "com.VCB", title: String? = "Vietcombank") =
        ingestNotification(pkg, title, text, Instant.parse("2026-11-16T02:15:00Z"))

    @Test
    fun `cung mot tin den hai lan chi ra mot giao dich nhap`() = runTest {
        val device = Device().apply { setUp() }
        val ingestor = with(device) { ingestor() }

        assertEquals(IngestOutcome.DRAFTED, ingestor.post(sms))
        assertEquals(IngestOutcome.ALREADY_EXISTS, ingestor.post(sms))

        val rows = device.transactions()
        assertEquals(1, rows.size)
        assertEquals("DRAFT", rows.single().status)
        assertEquals(45_000, rows.single().amount_minor)
        assertEquals("wallet", rows.single().account_id)
        assertEquals(CaptureStatus.DRAFTED.name, device.events().single().status)
        assertNull(device.events().single().raw_text)
        // Nháp đi qua op CREATE: chỉ ghi khi server chưa có id đó.
        assertEquals(listOf("CREATE"), device.database.outboxQueries.selectAll().executeAsList().map { it.action })
    }

    @Test
    fun `hai may cung nhan mot tin thi ra cung id`() = runTest {
        val phone = Device().apply { setUp() }
        val tablet = Device().apply { setUp() }

        with(phone) { ingestor() }.post(sms)
        with(tablet) { ingestor() }.post(sms)

        assertEquals(phone.transactions().single().id, tablet.transactions().single().id)
    }

    @Test
    fun `hai tai khoan tren cung may khong dung id`() = runTest {
        val ann = Device("ann").apply { setUp() }
        val bob = Device("bob").apply { setUp() }

        with(ann) { ingestor() }.post(sms)
        with(bob) { ingestor() }.post(sms)

        assertTrue(ann.transactions().single().id != bob.transactions().single().id)
    }

    @Test
    fun `chua gan vi thi cho chon vi - gan xong thi ra nhap`() = runTest {
        val device = Device().apply { setUp(link = false) }
        val ingestor = with(device) { ingestor() }

        assertEquals(IngestOutcome.NEEDS_ACCOUNT, ingestor.post(sms))
        assertTrue(device.transactions().isEmpty())
        val waiting = device.events().single()
        assertEquals(CaptureStatus.NEEDS_ACCOUNT.name, waiting.status)
        assertNotNull(waiting.raw_text)

        val linked = with(device) { repository(ingestor) }.linkAccount(BankId.VIETCOMBANK, "5678", "wallet")

        assertEquals(AppResult.Success(1), linked)
        assertEquals(1, device.transactions().size)
        assertEquals(CaptureStatus.DRAFTED.name, device.events().single().status)
        assertNull(device.events().single().raw_text)
        // Lần sau tự vào đúng ví.
        assertEquals(IngestOutcome.DRAFTED, ingestor.post(sms.replace("45,000", "46,000").replace("1,189,567", "1,143,567")))
    }

    @Test
    fun `vi da luu tru thi cho chon vi`() = runTest {
        val device = Device().apply { setUp(archived = true) }

        assertEquals(IngestOutcome.NEEDS_ACCOUNT, with(device) { ingestor() }.post(sms))
        assertTrue(device.transactions().isEmpty())
    }

    @Test
    fun `da bo qua roi thi tin den lai khong hoi sinh - ke ca sau khi don nhat ky`() = runTest {
        val device = Device().apply { setUp() }
        val ingestor = with(device) { ingestor() }
        ingestor.post(sms)
        val id = device.transactions().single().id
        device.database.ledgerTransactionQueries.markDeleted(id) // user bấm Bỏ qua
        clock.advanceBy(31.days)
        ingestor.purge(device.database)
        assertTrue(device.events().isEmpty())

        assertEquals(IngestOutcome.ALREADY_EXISTS, ingestor.post(sms))
        assertTrue(device.transactions().isEmpty())
    }

    @Test
    fun `OTP khong de lai dong nao`() = runTest {
        val device = Device().apply { setUp() }

        assertEquals(IngestOutcome.OTP, with(device) { ingestor() }.post("Ma OTP: 123456. Khong chia se cho ai. -45,000VND"))
        assertTrue(device.events().isEmpty())
        assertTrue(device.transactions().isEmpty())
    }

    @Test
    fun `khong co phien thi khong ghi gi`() = runTest {
        val device = Device().apply { setUp() }
        device.session.state.value = LocalDataState.SignedOut

        assertEquals(IngestOutcome.NO_SESSION, with(device) { ingestor() }.post(sms))
        assertTrue(device.events().isEmpty())
    }

    @Test
    fun `chua bat hoac ngan hang chua chon thi bo qua`() = runTest {
        val device = Device().apply { setUp() }
        val ingestor = with(device) { ingestor() }

        assertEquals(IngestOutcome.DISABLED, ingestor.post(sms, pkg = "com.mbmobile"))
        device.settings.setEnabled(false)
        assertEquals(IngestOutcome.DISABLED, ingestor.post(sms))
        assertTrue(device.transactions().isEmpty())
    }

    @Test
    fun `tin khong doc duoc giu nguyen van toi da 14 ngay`() = runTest {
        val device = Device().apply { setUp() }
        val ingestor = with(device) { ingestor() }

        assertEquals(IngestOutcome.UNPARSED, ingestor.post("Bien dong so du TK. Mo app de xem chi tiet"))
        assertNotNull(device.events().single().raw_text)

        clock.advanceBy(15.days)
        ingestor.purge(device.database)
        assertNull(device.events().single().raw_text)
    }

    @Test
    fun `thong bao cua app khong co so tien`() = runTest {
        val device = Device().apply { setUp() }
        with(device) { ingestor() }.post(sms)

        val shown = device.notifications.groups.getValue(CaptureInboxNotifier.GROUP).single()
        assertFalse("45" in shown.body)
        assertEquals(CaptureInboxNotifier.ACTION_OPEN_INBOX, shown.action)
    }

    @Test
    fun `SMS chi doc khi dung nguoi gui cua ngan hang da chon`() = runTest {
        val device = Device().apply { setUp() }
        val ingress: NotificationIngress = CaptureNotificationIngress(device.settings, with(device) { ingestor() }, CaptureEnvironment(debug = false), this)

        assertTrue(ingress.accepts("com.VCB"))
        assertFalse(ingress.accepts("com.mbmobile"))
        assertTrue(ingress.accepts("com.google.android.apps.messaging"))
        assertTrue(ingress.acceptsSender("com.google.android.apps.messaging", "Vietcombank"))
        assertFalse(ingress.acceptsSender("com.google.android.apps.messaging", "Me"))
        assertFalse(ingress.accepts("com.android.shell")) // bản release không nhận thông báo demo
    }

    @Test
    fun `xoa user thi danh sach package cap may bo theo`() = runTest {
        val device = Device().apply { setUp() }
        assertTrue("com.VCB" in device.settings.devicePackages())

        CaptureUserDataCleaner(device.settingsStore, device.deviceKeys).clear(USER)

        assertTrue(device.settings.devicePackages().isEmpty())
    }

    private companion object {
        const val USER = "user-1"
    }
}

private class FakeSession(initial: LocalDataState) : UserSession {
    override val state = MutableStateFlow(initial)
    override val notice: StateFlow<SessionNotice?> = MutableStateFlow(null)
    override fun start() = Unit
    override fun refresh() = Unit
    override fun acknowledgeNotice() = Unit
    override suspend fun wipeCurrentUser() = Unit
    override suspend fun discardDeletedAccount() = Unit
    override suspend fun otherUsersOnDevice(currentUserId: String): Set<String> = emptySet()
    override suspend fun usersOnDevice(): Set<String> = emptySet()
    override suspend fun wipe(userIds: Set<String>) = Unit
}

private class RecordingScheduler : LocalNotificationScheduler {
    val groups = mutableMapOf<String, List<LocalNotification>>()
    override suspend fun replaceGroup(group: String, notifications: List<LocalNotification>) { groups[group] = notifications }
    override suspend fun clearDelivered(group: String) = Unit
}

private object NoopScheduler : SyncScheduler {
    override fun schedulePeriodic() = Unit
    override fun requestImmediate() = Unit
    override fun cancelAll() = Unit
}

private object NoopLedgerCodec : SyncPayloadCodec {
    override fun encode(payload: AccountPayload): JsonObject = JsonObject(emptyMap())
    override fun encode(payload: CategoryPayload): JsonObject = JsonObject(emptyMap())
    override fun encode(payload: TransactionPayload): JsonObject = JsonObject(emptyMap())
    override fun decodeAccount(data: JsonObject): AccountPayload = error("unused")
    override fun decodeCategory(data: JsonObject): CategoryPayload = error("unused")
    override fun decodeTransaction(data: JsonObject): TransactionPayload = error("unused")
}

private class MemorySettings : Settings {
    private val values = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = values.keys
    override val size: Int get() = values.size
    override fun clear() = values.clear()
    override fun remove(key: String) { values.remove(key) }
    override fun hasKey(key: String) = key in values
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String) = values[key] as? Int
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun getLong(key: String, defaultValue: Long) = values[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String) = values[key] as? Long
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getString(key: String, defaultValue: String) = values[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String) = values[key] as? String
    override fun putFloat(key: String, value: Float) { values[key] = value }
    override fun getFloat(key: String, defaultValue: Float) = values[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String) = values[key] as? Float
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun getDouble(key: String, defaultValue: Double) = values[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String) = values[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String) = values[key] as? Boolean
}
