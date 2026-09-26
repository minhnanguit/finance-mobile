import com.uit.finance.buildlogic.libs
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP library that ships Compose Multiplatform UI (core/designsystem, features).
 */
plugins {
    id("finance.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        implementation(libs.findLibrary("compose-runtime").get())
        implementation(libs.findLibrary("compose-foundation").get())
        implementation(libs.findLibrary("compose-material3").get())
        implementation(libs.findLibrary("compose-ui").get())
        implementation(libs.findLibrary("jetbrains-lifecycle-runtime-compose").get())
    }
}
