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
    buildTypes {
        // Debug gọi backend + Keycloak local qua http (10.0.2.2). Dùng placeholder thay vì manifest
        // riêng ở src/androidDebug: trong setup KMP này file đó KHÔNG được merge (đã kiểm bằng
        // manifest-merger report), nên cờ cleartext chưa bao giờ có hiệu lực.
        getByName("debug") { manifestPlaceholders["usesCleartextTraffic"] = "true" }
        getByName("release") { manifestPlaceholders["usesCleartextTraffic"] = "false" }
    }
}
