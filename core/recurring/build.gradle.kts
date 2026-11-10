plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.database) // bảng recurring nằm chung FinanceDatabase với outbox
            implementation(projects.core.sync) // OutboxWriter, SyncChangeApplier, SyncScheduler, SyncEngine
            implementation(projects.core.network) // RecurringPayloadCodec: payload đi qua model của contract
            implementation(projects.core.ledger) // LedgerOccurrenceWriter: ghi kỳ trong cùng transaction (RS13)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.turbine)
        }
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
