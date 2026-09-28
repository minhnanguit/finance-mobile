plugins {
    alias(libs.plugins.finance.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// OIDC client cho Keycloak (ADR-004): Authorization Code + PKCE qua system browser, refresh token,
// logout. Module DUY NHẤT được đụng tới Custom Tabs / ASWebAuthenticationSession (Konsist chặn).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.network) // AuthTokens, TokenRefresher nằm trong public API của module này
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.browser)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
