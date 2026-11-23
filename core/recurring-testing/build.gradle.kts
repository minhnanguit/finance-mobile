plugins {
    alias(libs.plugins.finance.kmp.library)
}

// Fake repository của core/recurring cho commonTest của các feature (chỉ dùng trong test).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.recurring)
            api(projects.core.testing)
        }
    }
}
