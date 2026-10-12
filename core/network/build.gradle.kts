import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.finance.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

val generatedPackage = "com.uit.finance.core.network.generated"
val generatedDir = layout.buildDirectory.dir("generated/openapi")

// Contract-first: Kotlin client được generate từ spec đã pin trong /api ở mỗi lần build.
val openApiGenerate = tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set(rootProject.layout.projectDirectory.file("api/openapi.yaml").asFile.absolutePath)
    outputDir.set(generatedDir.map { it.asFile.absolutePath })
    // Xoá output cũ trước khi generate: không có dòng này thì class của path đã bị gỡ khỏi spec
    // (ví dụ AuthApi sau contract 2.0.0) vẫn nằm lại và vẫn compile.
    cleanupOutput.set(true)
    packageName.set(generatedPackage)
    apiPackage.set("$generatedPackage.api")
    modelPackage.set("$generatedPackage.model")
    invokerPackage.set("$generatedPackage.infrastructure")
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    generateApiTests.set(false)
    generateModelTests.set(false)
    // kotlinx-datetime 0.7+ đã chuyển Instant vào stdlib (kotlin.time.Instant), kotlinx-serialization 1.9+
    // serialize được trực tiếp; map để field date-time giữ đúng type thay vì rơi về String.
    typeMappings.set(
        mapOf(
            "date-time" to "kotlin.time.Instant",
            "DateTime" to "kotlin.time.Instant",
            // Contract 2.1.0: `data` của op sync là object tự do (`additionalProperties: true`). Mặc định
            // generator sinh `Map<String, Any>`, kotlinx-serialization không serialize được `Any`.
            // JsonElement giữ nguyên kiểu JSON (số nguyên vẫn là số nguyên, không bị ép sang Double).
            "AnyType" to "JsonElement",
        ),
    )
    // Tên ngắn ở typeMappings + import ở đây: đưa tên đầy đủ vào typeMappings thì generator coi là tên
    // model và sinh ra `KotlinxserializationjsonJsonElement`.
    importMappings.set(mapOf("JsonElement" to "kotlinx.serialization.json.JsonElement"))
    configOptions.set(
        mapOf(
            // Không set serializationLibrary: library=multiplatform đã ngụ ý kotlinx-serialization,
            // set thêm thì template sinh @Serializable hai lần.
            "dateLibrary" to "kotlinx-datetime",
            "enumPropertyNaming" to "UPPERCASE",
            "nonPublicApi" to "true", // generated type là `internal`: feature không import được
            "omitGradleWrapper" to "true",
            "omitGradlePluginVersions" to "true",
            "sourceFolder" to "src/commonMain/kotlin",
        ),
    )
}

kotlin {
    sourceSets {
        commonMain {
            // Đưa output của generator vào compile và bắt mọi compile task phụ thuộc vào nó.
            kotlin.srcDir(openApiGenerate.map { generatedDir.get().dir("src/commonMain/kotlin") })
            dependencies {
                api(projects.core.common)
                api(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.koin.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
    }
}
