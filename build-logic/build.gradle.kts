plugins {
    `kotlin-dsl`
}

group = "com.uit.finance.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("financeKmpLibrary") {
            id = "finance.kmp.library"
            implementationClass = "com.uit.finance.buildlogic.KmpLibraryPlugin"
        }
        register("financeKmpCompose") {
            id = "finance.kmp.compose"
            implementationClass = "com.uit.finance.buildlogic.KmpComposePlugin"
        }
        register("financeKmpFeature") {
            id = "finance.kmp.feature"
            implementationClass = "com.uit.finance.buildlogic.KmpFeaturePlugin"
        }
        register("financeComposeApplication") {
            id = "finance.compose.application"
            implementationClass = "com.uit.finance.buildlogic.ComposeApplicationPlugin"
        }
    }
}
