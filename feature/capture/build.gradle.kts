plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Chỉ dùng package domain của core/capture, core/ledger, core/notification.
            implementation(projects.core.capture)
            implementation(projects.core.ledger)
            implementation(projects.core.notification)
            implementation(projects.core.ui)
        }
        commonTest.dependencies {
            implementation(projects.core.ledgerTesting)
        }
    }
}
