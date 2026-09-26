plugins {
    alias(libs.plugins.finance.kmp.library)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("FinanceDatabase") {
            packageName.set("com.uit.finance.core.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/schema"))
            verifyMigrations.set(false)
        }
    }
    linkSqlite.set(true)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}
