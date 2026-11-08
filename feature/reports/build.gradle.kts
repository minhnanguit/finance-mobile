plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Chỉ dùng package domain của core/ledger (model + repository interface); data của nó là internal.
            implementation(projects.core.ledger)
            implementation(projects.core.ui)
        }
        commonTest.dependencies {
            implementation(projects.core.ledgerTesting)
        }
    }
}
