plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // data layer only; presentation/domain must not touch these (enforced by architecture tests)
            implementation(projects.core.network)
            implementation(projects.core.datastore)
        }
    }
}
