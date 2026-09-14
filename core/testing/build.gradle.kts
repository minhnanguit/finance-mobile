plugins {
    alias(libs.plugins.finance.kmp.library)
}

// Test-support library: fakes and rules consumed from other modules' test source sets.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.kotlinx.coroutines.test)
            api(libs.kotlin.test)
        }
        androidMain.dependencies {
            api(libs.junit)
        }
    }
}
