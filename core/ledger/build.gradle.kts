plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.database) // bảng ledger nằm chung FinanceDatabase với outbox
            implementation(projects.core.sync) // OutboxWriter, SyncChangeApplier, SyncScheduler
            implementation(projects.core.network) // SyncPayloadCodec: payload đi qua model của contract
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
