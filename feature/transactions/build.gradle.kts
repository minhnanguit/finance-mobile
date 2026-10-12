plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Chỉ dùng package domain của core/ledger và core/sync; data của chúng là internal.
            implementation(projects.core.ledger)
            implementation(projects.core.sync)
            implementation(projects.core.ui)
        }
        commonTest.dependencies {
            implementation(projects.core.ledgerTesting)
        }
    }
}
