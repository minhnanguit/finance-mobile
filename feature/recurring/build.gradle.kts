plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Chỉ dùng package domain (+ reminder) của core/recurring, core/ledger, core/sync; data của chúng là internal.
            implementation(projects.core.recurring)
            implementation(projects.core.ledger)
            implementation(projects.core.sync)
            implementation(projects.core.ui)
        }
        commonTest.dependencies {
            implementation(projects.core.recurringTesting)
            implementation(projects.core.ledgerTesting)
        }
    }
}
