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
        // Benchmark tìm kiếm trên SQLCipher thật (docs/SEARCH-PLAN.md Phase 3). Cần máy ảo: `make bench`.
        androidInstrumentedTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.junit)
        }
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
