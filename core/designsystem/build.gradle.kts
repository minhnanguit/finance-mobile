plugins {
    alias(libs.plugins.finance.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.presentation) // UiText rendering helpers
        }
    }
}
