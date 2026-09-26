package com.uit.finance.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Root package of the app; every module namespace is derived from it. */
const val BASE_PACKAGE = "com.uit.finance"

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

fun VersionCatalog.versionInt(alias: String): Int = version(alias).toInt()

/** `:core:common` -> `com.uit.finance.core.common` */
val Project.androidNamespace: String
    get() = BASE_PACKAGE + path.replace(':', '.').replace('-', '_')
