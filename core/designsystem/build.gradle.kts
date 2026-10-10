plugins {
    alias(libs.plugins.finance.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.presentation) // UiText rendering helpers
            // Font Montserrat (OFL, xem FONT-LICENSE-Montserrat-OFL.txt) nằm trong composeResources/font.
            implementation(libs.compose.components.resources)
            // Bộ icon Lucide: chỉ module này được import; mọi nơi khác đi qua FinanceIcons.
            implementation(libs.composables.icons.lucide)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.uit.finance.core.designsystem.resources"
    generateResClass = always
}
