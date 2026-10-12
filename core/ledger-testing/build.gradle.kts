plugins {
    alias(libs.plugins.finance.kmp.library)
}

// Fake repository của core/ledger + core/sync cho commonTest của các feature (giống core/testing: chỉ dùng trong test).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.ledger)
            api(projects.core.sync)
            api(projects.core.testing)
        }
    }
}
