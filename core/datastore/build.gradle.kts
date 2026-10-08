plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.network) // TokenProvider port
            implementation(projects.core.database) // DatabaseKeyStore port
            api(libs.multiplatform.settings)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.turbine)
        }
    }
}
