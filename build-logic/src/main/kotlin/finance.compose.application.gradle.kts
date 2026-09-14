import com.android.build.api.dsl.ApplicationExtension
import com.mosaicglobal.finance.buildlogic.BASE_PACKAGE
import com.mosaicglobal.finance.buildlogic.configureAndroid
import com.mosaicglobal.finance.buildlogic.configureKotlinMultiplatform
import com.mosaicglobal.finance.buildlogic.libs
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The single application module: Android app + iOS framework (`ComposeApp`).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

extensions.configure<KotlinMultiplatformExtension> {
    configureKotlinMultiplatform(this)

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets.apply {
        commonMain.dependencies {
            implementation(libs.findLibrary("compose-runtime").get())
            implementation(libs.findLibrary("compose-foundation").get())
            implementation(libs.findLibrary("compose-material3").get())
            implementation(libs.findLibrary("compose-ui").get())
            implementation(libs.findLibrary("jetbrains-lifecycle-runtime-compose").get())
            implementation(libs.findLibrary("jetbrains-lifecycle-viewmodel-compose").get())
            implementation(libs.findLibrary("jetbrains-navigation-compose").get())
            implementation(libs.findLibrary("koin-core").get())
            implementation(libs.findLibrary("koin-compose").get())
            implementation(libs.findLibrary("koin-compose-viewmodel").get())
            implementation(libs.findLibrary("kotlinx-serialization-json").get())
        }
        androidMain.dependencies {
            implementation(libs.findLibrary("androidx-activity-compose").get())
            implementation(libs.findLibrary("kotlinx-coroutines-android").get())
            implementation(libs.findLibrary("koin-android").get())
            implementation(libs.findLibrary("koin-androidx-workmanager").get())
        }
    }
}

extensions.configure<ApplicationExtension> {
    namespace = BASE_PACKAGE
    configureAndroid(this)
    defaultConfig {
        applicationId = BASE_PACKAGE
        targetSdk = libs.findVersion("android-targetSdk").get().requiredVersion.toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}
