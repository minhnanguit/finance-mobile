plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Chỉ dùng model + repository interface (package domain) của core/ledger; data của nó là internal.
            implementation(projects.core.ledger)
        }
    }
}
