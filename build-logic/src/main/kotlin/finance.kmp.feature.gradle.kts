import com.mosaicglobal.finance.buildlogic.libs
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Feature module: Compose UI + MVI ViewModels + Koin module + kotlinx-serialization for type-safe routes.
 * Package layout inside the module: `domain` / `data` / `presentation`.
 */
plugins {
    id("finance.kmp.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.apply {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:presentation"))
            implementation(project(":core:designsystem"))

            implementation(libs.findLibrary("koin-core").get())
            implementation(libs.findLibrary("koin-core-viewmodel").get())
            implementation(libs.findLibrary("koin-compose").get())
            implementation(libs.findLibrary("koin-compose-viewmodel").get())

            implementation(libs.findLibrary("jetbrains-lifecycle-viewmodel").get())
            implementation(libs.findLibrary("jetbrains-lifecycle-viewmodel-compose").get())
            implementation(libs.findLibrary("jetbrains-navigation-compose").get())
            implementation(libs.findLibrary("kotlinx-serialization-json").get())
            implementation(libs.findLibrary("kotlinx-datetime").get())
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.findLibrary("turbine").get())
        }
    }
}
