plugins {
    alias(libs.plugins.finance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.koin.core) // corePresentationModule (MoneyFormatter)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.turbine)
        }
    }
}
