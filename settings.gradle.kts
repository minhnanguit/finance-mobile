rootProject.name = "finance-mobile"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":core:common")
include(":core:presentation")
include(":core:network")
include(":core:auth")
include(":core:database")
include(":core:datastore")
include(":core:sync")
include(":core:ledger")
include(":core:designsystem")
include(":core:testing")
include(":feature:auth")
include(":feature:home")
include(":architecture-test")
