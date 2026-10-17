plugins {
    alias(libs.plugins.finance.kmp.library)
}

// Fake repository của core/budget cho commonTest của các feature (giống core/ledger-testing: chỉ dùng trong test).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.budget)
            api(projects.core.testing)
        }
    }
}
