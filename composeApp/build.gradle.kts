plugins {
    alias(libs.plugins.finance.compose.application)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.presentation)
            implementation(projects.core.designsystem)
            implementation(projects.core.network)
            implementation(projects.core.auth)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.sync)
            implementation(projects.feature.auth)
        }
    }
}

android {
    buildFeatures {
        buildConfig = true
    }
}
