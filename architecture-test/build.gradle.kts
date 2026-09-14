plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Konsist reads the Kotlin sources of every module in the repo (not the compiled classes),
// so this module has no dependency on the modules it verifies.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnit()
    // Konsist resolves the project root from the working directory.
    workingDir = rootDir
    // Konsist parses the *sources* of the other modules, which are not on this task's classpath:
    // declare them as inputs so the task re-runs whenever any module changes (no stale UP-TO-DATE).
    inputs.files(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "**/.gradle/**")
        },
    ).withPropertyName("scannedKotlinSources").withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
