import com.android.build.api.dsl.LibraryExtension
import com.mosaicglobal.finance.buildlogic.androidNamespace
import com.mosaicglobal.finance.buildlogic.configureAndroid
import com.mosaicglobal.finance.buildlogic.configureKotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Plain Kotlin Multiplatform library (Android + iOS). No Compose, no DI.
 * Used by core/common, core/presentation, core/network, core/database, core/datastore, core/sync, core/testing.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

extensions.configure<KotlinMultiplatformExtension> {
    configureKotlinMultiplatform(this)
}

extensions.configure<LibraryExtension> {
    namespace = androidNamespace
    configureAndroid(this)
}
