package com.uit.finance.core.budget.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.network.api.model.BudgetPayload
import com.uit.finance.core.network.api.model.BudgetPayloadCodec
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Schema thật trên SQLite in-memory của JVM (không có SQLCipher, không cần ở đây). */
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
 * Codec giả: giữ payload thật trong bộ nhớ, JSON chỉ mang số tham chiếu. Codec thật (đi qua model của contract)
 * có test riêng ở core/network.
 */
class FakeBudgetCodec : BudgetPayloadCodec {
    val payloads = mutableListOf<BudgetPayload>()

    override fun encode(payload: BudgetPayload): JsonObject {
        payloads += payload
        return buildJsonObject { put("ref", JsonPrimitive(payloads.lastIndex)) }
    }

    override fun decodeBudget(data: JsonObject): BudgetPayload = payloads[data.getValue("ref").jsonPrimitive.int]
}
