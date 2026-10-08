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
    // KHÔNG link libsqlite3 của hệ thống: DB của user phải đi qua SQLCipher (ADR-006 B6). iOS link SQLCipher
    // ở iosApp/project.yml; link nhầm SQLite thường thì UserDatabaseManager từ chối mở (PRAGMA cipher_version).
    linkSqlite.set(false)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqlcipher.android)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        androidUnitTest.dependencies {
            // SQLite in-memory qua JDBC: chạy schema thật trên JVM của máy dev (không có SQLCipher ở đây).
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
