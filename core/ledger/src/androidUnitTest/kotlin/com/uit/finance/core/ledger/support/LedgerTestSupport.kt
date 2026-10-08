package com.uit.finance.core.ledger.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.CategoryPayload
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

fun inMemoryDatabase(): FinanceDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    FinanceDatabase.Schema.create(driver)
    return FinanceDatabase(driver)
}

class FakeDatabases(database: FinanceDatabase? = inMemoryDatabase()) : UserDatabaseProvider {
    override val database = MutableStateFlow(database)
}

class FakeScheduler : SyncScheduler {
    var immediateRequests = 0
    override fun schedulePeriodic() = Unit
    override fun requestImmediate() {
        immediateRequests++
    }
    override fun cancelAll() = Unit
}

/**
 * Codec giả: giữ payload thật trong bộ nhớ, JSON chỉ mang số tham chiếu. Codec thật (đi qua model của
 * contract) có test riêng ở core/network.
 */
class FakeCodec : SyncPayloadCodec {
    val payloads = mutableListOf<Any>()

    private fun ref(payload: Any): JsonObject {
        payloads += payload
        return buildJsonObject { put("ref", JsonPrimitive(payloads.lastIndex)) }
    }

    private inline fun <reified T> deref(data: JsonObject): T = payloads[data.getValue("ref").jsonPrimitive.int] as T

    override fun encode(payload: AccountPayload) = ref(payload)
    override fun encode(payload: CategoryPayload) = ref(payload)
    override fun encode(payload: TransactionPayload) = ref(payload)
    override fun decodeAccount(data: JsonObject): AccountPayload = deref(data)
    override fun decodeCategory(data: JsonObject): CategoryPayload = deref(data)
    override fun decodeTransaction(data: JsonObject): TransactionPayload = deref(data)
}

internal fun ledgerStore(databases: UserDatabaseProvider, scheduler: SyncScheduler, dispatchers: DispatcherProvider) =
    LedgerStore(databases = databases, scheduler = scheduler, dispatchers = dispatchers)
