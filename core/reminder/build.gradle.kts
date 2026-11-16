plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.notification)
            implementation(projects.core.datastore)
            implementation(projects.core.ledger)
            implementation(projects.core.session)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(projects.core.ledgerTesting)
            implementation(libs.turbine)
        }
    }
}
