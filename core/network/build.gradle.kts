import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.finance.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

val generatedPackage = "com.mosaicglobal.finance.core.network.generated"
val generatedDir = layout.buildDirectory.dir("generated/openapi")

// Contract-first: the Kotlin client is generated from the pinned spec in /api on every build.
val openApiGenerate = tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set(rootProject.layout.projectDirectory.file("api/openapi.yaml").asFile.absolutePath)
    outputDir.set(generatedDir.map { it.asFile.absolutePath })
    packageName.set(generatedPackage)
    apiPackage.set("$generatedPackage.api")
    modelPackage.set("$generatedPackage.model")
    invokerPackage.set("$generatedPackage.infrastructure")
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    generateApiTests.set(false)
    generateModelTests.set(false)
    // kotlinx-datetime 0.7+ moved Instant into the stdlib (kotlin.time.Instant), which kotlinx-serialization
    // 1.9+ serialises natively; keep the spec's date-time fields typed instead of falling back to String.
    typeMappings.set(
        mapOf(
            "date-time" to "kotlin.time.Instant",
            "DateTime" to "kotlin.time.Instant",
        ),
    )
    configOptions.set(
        mapOf(
            // NOTE: do not set serializationLibrary here: library=multiplatform already implies
            // kotlinx-serialization and setting it explicitly makes the template emit @Serializable twice.
            "dateLibrary" to "kotlinx-datetime",
            "enumPropertyNaming" to "UPPERCASE",
            "nonPublicApi" to "true", // generated types are `internal`: features cannot import them
            "omitGradleWrapper" to "true",
            "omitGradlePluginVersions" to "true",
            "sourceFolder" to "src/commonMain/kotlin",
        ),
    )
}

kotlin {
    sourceSets {
        commonMain {
            // Wires the generator output into compilation and makes every compile task depend on it.
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
