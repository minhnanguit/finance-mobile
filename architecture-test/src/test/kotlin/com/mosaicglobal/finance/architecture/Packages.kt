package com.mosaicglobal.finance.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

internal const val ROOT = "com.mosaicglobal.finance"
internal const val FEATURE_PREFIX = "$ROOT.feature."
internal const val CORE_PREFIX = "$ROOT.core."
internal const val GENERATED_PREFIX = "$ROOT.core.network.generated."

/** Production sources of every module (test source sets excluded). */
internal val productionScope: KoScope by lazy { Konsist.scopeFromProduction() }

internal val KoFileDeclaration.packageName: String
    get() = packagee?.name.orEmpty()

internal fun KoFileDeclaration.inPackageSegment(segment: String): Boolean =
    packageName.split('.').contains(segment)

/** `com.mosaicglobal.finance.feature.auth.domain.X` -> `auth` */
internal fun featureOf(fqName: String): String? =
    fqName.takeIf { it.startsWith(FEATURE_PREFIX) }
        ?.removePrefix(FEATURE_PREFIX)
        ?.substringBefore('.')
