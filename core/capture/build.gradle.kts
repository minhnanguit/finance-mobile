plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.database) // bảng capture_* nằm chung FinanceDatabase của user
            implementation(projects.core.ledger) // LedgerOccurrenceWriter (ghi nháp + op CREATE), ví, danh mục, giao dịch
            implementation(projects.core.sync) // SyncScheduler: hẹn sync sau khi ghi nháp
            implementation(projects.core.session) // UserSession (chờ sổ mở), UserDataCleaner
            implementation(projects.core.datastore) // cài đặt theo user (multiplatform-settings)
            implementation(projects.core.notification) // NotificationIngress + thông báo "Có N giao dịch"
            implementation(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.turbine)
        }
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(projects.core.network) // codec rỗng cho LedgerOccurrenceWriter thật
            implementation(projects.core.ledgerTesting)
        }
    }
}
