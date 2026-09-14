package com.mosaicglobal.finance.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/** ARCHITECTURE.md §6.2: presentation -> domain <- data; features are isolated; core never knows features. */
class LayerDependencyRulesTest {

    @Test
    fun `presentation does not depend on data`() {
        productionScope.files
            .filter { it.inPackageSegment("presentation") }
            .assertFalse { file ->
                file.hasImport { import -> import.name.startsWith(ROOT) && import.name.split('.').contains("data") }
            }
    }

    @Test
    fun `domain is pure Kotlin - no ktor, sqldelight, compose, koin, android, ui or data imports`() {
        val forbiddenPrefixes = listOf(
            "io.ktor.",
            "app.cash.sqldelight.",
            "androidx.",
            "android.",
            "org.jetbrains.compose.",
            "org.koin.",
            "platform.", // Kotlin/Native Apple frameworks
            "kotlinx.serialization.",
            "com.russhwolf.settings.",
        )
        productionScope.files
            .filter { it.inPackageSegment("domain") }
            .assertFalse { file ->
                file.hasImport { import ->
                    forbiddenPrefixes.any { import.name.startsWith(it) } ||
                        (import.name.startsWith(ROOT) && import.name.split('.').any { it == "data" || it == "presentation" })
                }
            }
    }

    @Test
    fun `feature modules never import other feature modules`() {
        productionScope.files
            .filter { it.packageName.startsWith(FEATURE_PREFIX) }
            .assertFalse { file ->
                val own = featureOf(file.packageName)
                file.hasImport { import -> featureOf(import.name)?.let { it != own } == true }
            }
    }

    @Test
    fun `core modules never depend on features`() {
        productionScope.files
            .filter { it.packageName.startsWith(CORE_PREFIX) }
            .assertFalse { file -> file.hasImport { it.name.startsWith(FEATURE_PREFIX) } }
    }

    @Test
    fun `generated OpenAPI client is only referenced inside core-network`() {
        productionScope.files
            .filter { !it.packageName.startsWith("$ROOT.core.network") }
            .assertFalse { file -> file.hasImport { it.name.startsWith(GENERATED_PREFIX) } }
    }

    @Test
    fun `only the network layer maps exceptions - no module outside core-network imports ApiException except data layers`() {
        productionScope.files
            .filter { !it.packageName.startsWith("$ROOT.core.network") && !it.inPackageSegment("data") }
            .assertFalse { file -> file.hasImport { it.name == "$ROOT.core.network.api.ApiException" } }
    }

    @Test
    fun `dispatchers are injected - kotlinx Dispatchers is only imported by DispatcherProvider and test support`() {
        productionScope.files
            .filter { file -> !file.path.endsWith("/DispatcherProvider.kt") && !file.path.contains("/core/testing/") }
            .assertFalse { file ->
                file.hasImport { it.name == "kotlinx.coroutines.Dispatchers" || it.name == "kotlinx.coroutines.IO" }
            }
    }

    @Test
    fun `GlobalScope is banned`() {
        productionScope.files.assertFalse { it.hasImport { import -> import.name == "kotlinx.coroutines.GlobalScope" } }
    }
}
