plugins {
    alias(libs.plugins.finance.kmp.feature)
}

compose.resources {
    packageOfResClass = "com.uit.finance.feature.auth.generated.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            // Chỉ data layer dùng; presentation/domain không được đụng (architecture test chặn)
            implementation(projects.core.auth)
            implementation(projects.core.network)
            implementation(projects.core.datastore)
            implementation(projects.core.session) // sổ trên máy của phiên (S6), sổ của tài khoản khác (B5)
        }
    }
}
