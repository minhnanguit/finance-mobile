package com.mosaicglobal.finance.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Common KMP target set-up shared by libraries and the application module:
 * Android + iOS (device + Apple-silicon simulator).
 */
fun Project.configureKotlinMultiplatform(kotlin: KotlinMultiplatformExtension) {
    kotlin.apply {
        androidTarget {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
        iosArm64()
        iosSimulatorArm64()

        applyDefaultHierarchyTemplate()

        compilerOptions {
            // expect/actual classes are still Beta; we rely on them for platform factories.
            freeCompilerArgs.add("-Xexpect-actual-classes")
            freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
        }

        sourceSets.apply {
            commonMain.dependencies {
                implementation(libs.findLibrary("kotlinx-coroutines-core").get())
            }
            commonTest.dependencies {
                implementation(libs.findLibrary("kotlin-test").get())
                implementation(libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}

/** Android defaults shared by `com.android.library` and `com.android.application`. */
fun Project.configureAndroid(android: CommonExtension<*, *, *, *, *, *>) {
    android.apply {
        compileSdk = libs.versionInt("android-compileSdk")
        defaultConfig {
            minSdk = libs.versionInt("android-minSdk")
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions {
            unitTests.isReturnDefaultValues = true
        }
    }
}
