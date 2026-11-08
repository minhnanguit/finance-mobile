plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Chỉ dùng package domain của core/budget, core/ledger và core/sync; data của chúng là internal.
            implementation(projects.core.budget)
            implementation(projects.core.ledger)
            implementation(projects.core.sync)
            implementation(projects.core.ui)
        }
        commonTest.dependencies {
            implementation(projects.core.budgetTesting)
            implementation(projects.core.ledgerTesting)
        }
    }
}
