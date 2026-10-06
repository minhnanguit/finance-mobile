package com.uit.finance.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class NamingAndPlacementRulesTest {

    @Test
    fun `classes named UseCase live in a domain package`() {
        productionScope.classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("..domain..") }
    }

    @Test
    fun `classes named ViewModel live in a presentation package`() {
        productionScope.classes()
            .withNameEndingWith("ViewModel")
            .assertTrue { it.resideInPackage("..presentation..") }
    }

    @Test
    fun `use cases expose a single invoke operator`() {
        productionScope.classes()
            .withNameEndingWith("UseCase")
            .assertTrue { useCase -> useCase.functions().count { it.name == "invoke" && it.hasOperatorModifier } == 1 }
    }

    @Test
    fun `repository implementations are internal and live in data`() {
        productionScope.classes()
            .withNameEndingWith("RepositoryImpl")
            .assertTrue { it.hasInternalModifier && it.resideInPackage("..data..") }
    }

    @Test
    fun `screen ViewModels are internal - only the Koin module is public`() {
        productionScope.classes()
            .withNameEndingWith("ViewModel")
            .filter { it.name != "MviViewModel" }
            .assertTrue { it.hasInternalModifier }
    }

    @Test
    fun `MVI state classes are immutable data classes`() {
        productionScope.classes()
            .withNameEndingWith("State")
            .filter { it.resideInPackage("..presentation..") }
            .assertTrue { state -> state.hasDataModifier && state.properties().all { it.isVal } }
    }

    @Test
    fun `navigation destinations are serializable and live in a navigation package`() {
        val destinations = productionScope.objects().withNameEndingWith("Destination") +
            productionScope.classes().withNameEndingWith("Destination").filter { !it.hasEnumModifier }
        destinations.assertTrue { it.hasAnnotationWithName("Serializable") && it.resideInPackage("..navigation..") }
    }

    @Test
    fun `each feature exposes exactly one public NavGraph entry point`() {
        val entryPoints = productionScope.functions()
            .filter { it.isTopLevel && it.receiverType?.name == "NavGraphBuilder" && !it.hasInternalModifier && !it.hasPrivateModifier }
            .filter { it.packagee?.name.orEmpty().startsWith(FEATURE_PREFIX) }
            .groupBy { featureOf(it.packagee!!.name) }
        val features = productionScope.files.mapNotNull { featureOf(it.packageName) }.toSet()

        features.forEach { feature ->
            val names = entryPoints[feature].orEmpty().map { it.name }
            assertEquals(listOf("${feature}NavGraph"), names, "feature '$feature' must expose exactly ${feature}NavGraph")
        }
    }

    @Test
    fun `Koin modules are declared once per Gradle module in a di package`() {
        productionScope.properties()
            .withNameEndingWith("Module")
            .filter { it.hasType { type -> type.name == "Module" } || it.text.contains("= module {") }
            .assertTrue { it.resideInPackage("..di..") }
    }
}
