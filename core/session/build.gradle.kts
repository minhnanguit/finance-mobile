plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.database) // UserDatabases
            implementation(projects.core.datastore) // SessionStore
            implementation(projects.core.network) // UserApi (/me) để biết userId
            implementation(projects.core.sync) // SyncScheduler
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.turbine)
        }
    }
}
