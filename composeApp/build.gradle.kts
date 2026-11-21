import java.util.Properties

plugins {
    alias(libs.plugins.finance.compose.application)
}

/*
 * URL inject lúc build (không hardcode), đọc từ `-P<key>=...` hoặc `<key>` trong local.properties (git-ignored).
 * - finance.publicBaseUrl:  HTTPS công khai cho build DEBUG (Cloudflare Tunnel, đổi mỗi lần bật lại —
 *                           `make tunnel URL=...` ghi hộ). Không có thì debug dùng loopback của emulator/simulator.
 * - finance.releaseBaseUrl: server production cho build RELEASE (một host cho cả API lẫn Keycloak, Caddy chia theo
 *                           path — xem finance-backend/deploy/PROD.md). Bắt buộc khi build release.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun httpsUrlProperty(key: String): String? =
    (providers.gradleProperty(key).orNull ?: localProperties.getProperty(key))
        ?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() }
        ?.also { require(it.startsWith("https://")) { "$key phải là https://…, nhận được: $it" } }

val publicBaseUrl: String? = httpsUrlProperty("finance.publicBaseUrl")
val releaseBaseUrl: String? = httpsUrlProperty("finance.releaseBaseUrl")

// Build release mà thiếu URL thì dừng ngay lúc build, không để app lên máy rồi mới hỏng.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { task ->
        task.project == project && (task.name.startsWith("assembleRelease") || task.name.startsWith("bundleRelease") ||
            task.name.startsWith("linkRelease") || task.name == "embedAndSignAppleFrameworkForXcode" &&
            System.getenv("CONFIGURATION") == "Release")
    }
    check(!buildsRelease || releaseBaseUrl != null) {
        "Build release cần finance.releaseBaseUrl (vd https://xxx.duckdns.org) trong local.properties hoặc -P."
    }
}

val appEnvironmentDir = layout.buildDirectory.dir("generated/appEnvironment/commonMain/kotlin")
val generateAppEnvironment by tasks.registering {
    fun literal(value: String?) = value?.let { "\"$it\"" } ?: "null"
    val publicLiteral = literal(publicBaseUrl)
    val releaseLiteral = literal(releaseBaseUrl)
    val outDir = appEnvironmentDir
    inputs.property("publicBaseUrl", publicBaseUrl ?: "")
    inputs.property("releaseBaseUrl", releaseBaseUrl ?: "")
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
            |    val publicBaseUrl: String? = $publicLiteral
            |
            |    /** Server production cho build release (API + Keycloak cùng host); `null` khi chưa cấu hình. */
            |    val releaseBaseUrl: String? = $releaseLiteral
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
            implementation(projects.core.ui)
            implementation(projects.core.network)
            implementation(projects.core.auth)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.notification)
            implementation(projects.core.reminder)
            implementation(projects.core.sync)
            implementation(projects.core.ledger)
            implementation(projects.core.budget)
            implementation(projects.core.recurring)
            implementation(projects.core.capture)
            implementation(projects.core.session)
            implementation(projects.feature.auth)
            implementation(projects.feature.home)
            implementation(projects.feature.accounts)
            implementation(projects.feature.transactions)
            implementation(projects.feature.budgets)
            implementation(projects.feature.recurring)
            implementation(projects.feature.capture)
            implementation(projects.feature.reports)
            implementation(projects.feature.settings)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
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
