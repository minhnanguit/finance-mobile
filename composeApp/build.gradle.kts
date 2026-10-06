import java.util.Properties

plugins {
    alias(libs.plugins.finance.compose.application)
}

/*
 * URL HTTPS công khai cho build debug (Cloudflare Tunnel), inject lúc build thay vì hardcode vì Quick Tunnel
 * đổi URL mỗi lần bật lại. Nguồn: `-Pfinance.publicBaseUrl=...` hoặc `finance.publicBaseUrl` trong
 * local.properties (git-ignored, `make tunnel URL=...` ghi hộ). Không có thì dùng loopback của emulator.
 */
val publicBaseUrl: String? = run {
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    (providers.gradleProperty("finance.publicBaseUrl").orNull ?: local.getProperty("finance.publicBaseUrl"))
        ?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() }
}
require(publicBaseUrl == null || publicBaseUrl.startsWith("https://")) {
    "finance.publicBaseUrl phải là https://…, nhận được: $publicBaseUrl"
}

val appEnvironmentDir = layout.buildDirectory.dir("generated/appEnvironment/commonMain/kotlin")
val generateAppEnvironment by tasks.registering {
    val literal = publicBaseUrl?.let { "\"$it\"" } ?: "null"
    val outDir = appEnvironmentDir
    inputs.property("publicBaseUrl", publicBaseUrl ?: "")
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/uit/finance/app/config/AppEnvironment.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// SINH TỰ ĐỘNG bởi task generateAppEnvironment (composeApp/build.gradle.kts) — ĐỪNG SỬA TAY.
            |package com.uit.finance.app.config
            |
            |internal object AppEnvironment {
            |    /** HTTPS công khai cho build debug; `null` = dùng loopback của emulator/simulator. */
            |    val publicBaseUrl: String? = $literal
            |}
            |""".trimMargin(),
        )
    }
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
            implementation(projects.feature.home)
        }
        commonMain {
            kotlin.srcDir(generateAppEnvironment)
        }
    }
}

android {
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        // Chỉ cho phép http:// khi debug VÀ không có URL HTTPS (tức đang gọi 10.0.2.2). Dùng placeholder thay
        // vì manifest riêng ở src/androidDebug: trong setup KMP này file đó KHÔNG được merge.
        getByName("debug") { manifestPlaceholders["usesCleartextTraffic"] = (publicBaseUrl == null).toString() }
        getByName("release") { manifestPlaceholders["usesCleartextTraffic"] = "false" }
    }
}
