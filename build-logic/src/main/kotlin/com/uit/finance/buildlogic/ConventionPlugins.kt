package com.uit.finance.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")

        extensions.configure<KotlinMultiplatformExtension> {
            configureKotlinMultiplatform(this)
        }

        extensions.configure<LibraryExtension> {
            namespace = androidNamespace
            configureAndroid(this)
        }
    }
}

class KmpComposePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("finance.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(libs.findLibrary("compose-runtime").get())
                implementation(libs.findLibrary("compose-foundation").get())
                implementation(libs.findLibrary("compose-material3").get())
                implementation(libs.findLibrary("compose-ui").get())
                implementation(libs.findLibrary("jetbrains-lifecycle-runtime-compose").get())
            }
        }
    }
}

class KmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("finance.kmp.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

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
    }
}

class ComposeApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

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
    }
}
