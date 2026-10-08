plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.database) // FinanceDatabase nằm trong API của OutboxWriter / SyncChangeApplier
            implementation(projects.core.network) // SyncApi
            implementation(projects.core.datastore) // installationId làm deviceId
            api(libs.kotlinx.serialization.json) // JsonObject trong payload của op
            implementation(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.work.runtime)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.turbine)
        }
        androidUnitTest.dependencies {
            // In-memory JDBC SQLite lets the outbox repository run against the real schema on the host JVM.
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
