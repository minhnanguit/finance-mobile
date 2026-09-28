plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Chỉ data layer dùng; presentation/domain không được đụng (architecture test chặn)
            implementation(projects.core.auth)
            implementation(projects.core.network)
            implementation(projects.core.datastore)
        }
    }
}
