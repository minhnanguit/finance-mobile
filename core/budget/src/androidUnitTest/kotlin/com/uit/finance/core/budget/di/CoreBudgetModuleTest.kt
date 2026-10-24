package com.uit.finance.core.budget.di

import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.budget.support.FakeBudgetCodec
import com.uit.finance.core.budget.support.FakeDatabases
import com.uit.finance.core.budget.support.FakeScheduler
import com.uit.finance.core.common.coroutines.DefaultDispatcherProvider
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.network.api.model.BudgetPayloadCodec
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.sync.engine.SyncChangeApplier
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.scheduler.SyncScheduler
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** SyncEngine chỉ áp được thay đổi `budget` khi applier có trong `getAll<SyncChangeApplier>()` (ADR-007 §8). */
class CoreBudgetModuleTest {

    private val dependencies = module {
        single<UserDatabaseProvider> { FakeDatabases() }
        single<SyncScheduler> { FakeScheduler() }
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        single<BudgetPayloadCodec> { FakeBudgetCodec() }
        single<UuidGenerator> { FakeUuidGenerator() }
        single { OutboxWriter(uuidGenerator = get(), clock = TestClock()) }
    }

    @Test
    fun `module đăng ký applier của budget và dựng được repository`() {
        val koin = koinApplication { modules(dependencies, coreBudgetModule) }.koin

        assertEquals(listOf(SyncEntity.BUDGET), koin.getAll<SyncChangeApplier>().map { it.entity })
        assertNotNull(koin.get<BudgetRepository>())
    }
}
